package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Selects sounds by matching per-file tags from TXT catalogs against the script and each scene. */
final class SoundApplicator {
    record Result(boolean musicAdded, Path musicFile, List<VideoProject.SoundEffect> effectsAdded) {
        int addedCount() { return effectsAdded.size() + (musicAdded ? 1 : 0); }
    }

    private record Sound(Path file, Set<String> tags) { }
    private record CatalogKey(Path directory, String filename) { }
    private static final Pattern ENTRY = Pattern.compile("(?m)^\\[(.+)]\\s*$");
    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_-]+)");
    private static final Set<String> MUSIC_TAGS = Set.of("trilha", "musica", "musica-de-fundo", "music", "background-music");
    private static final Set<String> EFFECT_TAGS = Set.of("efeito", "efeito-sonoro", "sound-effect", "effect");
    private static final Set<String> ROLE_TAGS = Set.of("trilha", "musica", "musica-de-fundo", "music", "background-music", "efeito", "efeito-sonoro", "sound-effect", "effect");
    private static final Set<String> IGNORED_WORDS = Set.of("de", "do", "da", "dos", "das", "e", "a", "o", "em", "no", "na", "um", "uma", "para", "com", "por", "the", "and", "of", "in", "for", "an");
    private static final double MIN_SCORE = 1.0;

    private SoundApplicator() { }

    static Result apply(VideoProject project, Path library) throws IOException {
        if (!Files.isDirectory(library) || !Files.isReadable(library))
            throw new IOException("A pasta de sons configurada não existe ou não pode ser lida: " + library);
        List<Sound> sounds = catalog(library);
        if (sounds.isEmpty()) throw new IOException("Não encontrei arquivos de áudio descritos nos TXT da pasta de sons.");

        boolean musicAdded = false;
        Path musicFile = null;
        if (project.music.file.isBlank()) {
            var scriptWords = words(project.script + " " + project.clips.stream().map(clip -> clip.text).reduce("", (a,b) -> a + " " + b));
            Sound music = sounds.stream().filter(sound -> hasRole(sound, MUSIC_TAGS))
                    .max(Comparator.comparingDouble(sound -> score(sound, scriptWords)))
                    .filter(sound -> score(sound, scriptWords) >= MIN_SCORE).orElse(null);
            if (music != null) {
                musicFile = music.file();
                project.music.file = musicFile.toAbsolutePath().toString();
                project.music.volume = .08;
                musicAdded = true;
            }
        }

        var added = new ArrayList<VideoProject.SoundEffect>();
        for (int scene = 0; scene < project.clips.size(); scene++) {
            var clip = project.clips.get(scene);
            var sceneWords = words(clip.text);
            Sound candidate = sounds.stream().filter(sound -> hasRole(sound, EFFECT_TAGS))
                    .filter(sound -> project.effects.stream().noneMatch(effect -> sameFile(effect.file, sound.file())))
                    .max(Comparator.comparingDouble(sound -> score(sound, sceneWords)))
                    .filter(sound -> score(sound, sceneWords) >= MIN_SCORE).orElse(null);
            if (candidate == null) continue;
            double start = sceneStart(project, scene);
            if (project.effects.stream().anyMatch(effect -> Math.abs(effect.start - start) < 1.0)) continue;
            var effect = new VideoProject.SoundEffect();
            effect.file = candidate.file().toAbsolutePath().toString();
            effect.start = Math.min(start + .4, start + clip.duration - .1);
            effect.offset = 0;
            effect.duration = Math.min(3, start + clip.duration - effect.start);
            effect.volume = .07;
            effect.fadeIn = Math.min(.2, effect.duration / 4);
            effect.fadeOut = Math.min(.7, effect.duration / 3);
            effect.validate();
            project.effects.add(effect);
            added.add(effect);
        }
        return new Result(musicAdded, musicFile, List.copyOf(added));
    }

    private static List<Sound> catalog(Path library) throws IOException {
        Map<CatalogKey, Set<String>> entries = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(library)) {
            for (Path txt : paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList()) {
                String content = Files.readString(txt);
                Matcher matcher = ENTRY.matcher(content);
                var headers = new ArrayList<int[]>();
                var filenames = new ArrayList<String>();
                while (matcher.find()) { headers.add(new int[]{matcher.start(), matcher.end()}); filenames.add(matcher.group(1).strip()); }
                for (int i = 0; i < headers.size(); i++) {
                    int end = headers.get(i)[1];
                    int nextHeader = i + 1 < headers.size() ? headers.get(i + 1)[0] : content.length();
                    int bodyStart = end;
                    while (bodyStart < nextHeader && (content.charAt(bodyStart) == '\r' || content.charAt(bodyStart) == '\n')) bodyStart++;
                    String body = content.substring(bodyStart, nextHeader);
                    Set<String> tags = new HashSet<>();
                    Matcher tagMatcher = TAG.matcher(body);
                    while (tagMatcher.find()) tags.add(normalize(tagMatcher.group(1)));
                    if (!tags.isEmpty()) entries.put(new CatalogKey(txt.getParent().toAbsolutePath().normalize(), filenames.get(i).toLowerCase(Locale.ROOT)), tags);
                }
            }
        }
        var sounds = new ArrayList<Sound>();
        try (Stream<Path> paths = Files.walk(library)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(SoundApplicator::isAudio).toList()) {
                Set<String> tags = entries.get(new CatalogKey(file.getParent().toAbsolutePath().normalize(), file.getFileName().toString().toLowerCase(Locale.ROOT)));
                if (tags != null) sounds.add(new Sound(file, Set.copyOf(tags)));
            }
        }
        sounds.sort(Comparator.comparing(sound -> sound.file().getFileName().toString().toLowerCase(Locale.ROOT)));
        return List.copyOf(sounds);
    }

    private static boolean hasRole(Sound sound, Set<String> roleTags) {
        return sound.tags().stream().anyMatch(roleTags::contains);
    }

    private static double score(Sound sound, Set<String> sceneWords) {
        Set<String> descriptiveTags = new HashSet<>(sound.tags());
        descriptiveTags.removeAll(ROLE_TAGS);
        double score = 0;
        for (String tag : descriptiveTags) {
            Set<String> terms = words(tag.replace('-', ' '));
            if (terms.isEmpty()) continue;
            long matched = terms.stream().filter(sceneWords::contains).count();
            if (matched == terms.size()) score += 1.0 + .25 * (terms.size() - 1);
            else score += .5 * matched / terms.size();
        }
        return score;
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(normalize(text));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() > 2 && !IGNORED_WORDS.contains(word)) result.add(word);
        }
        return result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean sameFile(String left, Path right) {
        return !left.isBlank() && Path.of(left).toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize());
    }

    private static double sceneStart(VideoProject project, int scene) {
        double start = 0;
        for (int i = 0; i < scene; i++) start += project.clips.get(i).duration;
        return start;
    }

    private static boolean isAudio(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") ||
                name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".aac");
    }
}
