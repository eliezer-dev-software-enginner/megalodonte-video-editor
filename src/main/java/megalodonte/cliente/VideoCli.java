package megalodonte.cliente;

import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Terminal workflow using the same project, image, sound and export services as the desktop editor. */
public final class VideoCli {
    private final BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final EditorSettings.Values settings;
    private final Map<String,String> options;
    private Process startedRessona;

    private VideoCli(Map<String,String> options) throws IOException {
        this.options=options;
        settings=new EditorSettings().load();
    }

    public static void main(String[] args) {
        try {
            var options=parse(args);
            if(options.containsKey("--help")) { help(); return; }
            new VideoCli(options).run();
        } catch (Exception error) {
            System.err.println("Falha: " + error.getMessage());
            System.exit(1);
        }
    }

    private void run() throws Exception {
        try {
            System.out.println("Megalodonte Video Editor · criação pelo terminal\n");
            String name=slug(value("--name","Nome do vídeo/projeto", "video-"+java.time.LocalDate.now()));
            String script=options.containsKey("--script-file")
                    ? Files.readString(Path.of(options.get("--script-file")),StandardCharsets.UTF_8)
                    : readScript();
            VideoProject project=VideoProject.fromScript(script);
            if(project.clips.isEmpty()) throw new IllegalArgumentException("O roteiro não contém cenas. Separe as cenas com uma linha vazia.");
            String avatar=avatarDirectory();
            if(!avatar.isBlank()) project.avatarDirectory=directory(avatar,"avatares").toString();
            for(var clip:project.clips) if(!clip.avatar.isBlank()) AvatarFiles.resolve(project.avatarDirectory,clip.avatar);

            String images=value("--images-dir","Pasta com imagens das cenas (vazio para gerar pela OpenAI)","");
            Path imageDirectory=images.isBlank()?null:directory(images,"imagens");
            if(imageDirectory!=null) assignImages(project,imageDirectory);
            String defaultEffect="smooth";
            String effect=value("--effect","Efeito visual: none, smooth, particles ou shake",defaultEffect).toLowerCase(Locale.ROOT);
            if(effect.equals("none")) effect="";
            VideoProject.validateVisualEffect(effect);
            for(var clip:project.clips) clip.visualEffect=effect;
            project.visualEffectStrength=settings.visualEffectStrength;
            String sounds=value("--sounds","Aplicar música e efeitos sonoros da biblioteca? (s/n)","n");
            boolean applySounds=sounds.equalsIgnoreCase("s") || sounds.equalsIgnoreCase("sim") || sounds.equalsIgnoreCase("yes");
            if(!applySounds && !sounds.equalsIgnoreCase("n") && !sounds.equalsIgnoreCase("não") && !sounds.equalsIgnoreCase("nao") && !sounds.equalsIgnoreCase("no"))
                throw new IllegalArgumentException("Responda s ou n para aplicar sons.");
            if(applySounds && settings.soundsDirectory.isBlank()) throw new IllegalArgumentException("Configure a biblioteca de sons em Configurações → Geral no editor.");

            System.out.printf("%n%d cenas; %s; efeito %s; sons %s.%n",project.clips.size(),
                    imageDirectory==null?"imagens pela OpenAI":"imagens locais",effect.isBlank()?"nenhum":effect,applySounds?"ativados":"desativados");
            if(options.containsKey("--dry-run")) {
                System.out.println("Validação concluída. Nenhum áudio, imagem ou vídeo foi gerado.");
                return;
            }
            if(imageDirectory==null) {
                String key=settings.apiKey.isBlank()?System.getenv().getOrDefault("OPENAI_API_KEY",""):settings.apiKey;
                if(key.isBlank()) throw new IOException("Configure a chave OpenAI nas Configurações para gerar as imagens.");
            }
            String url=value("--ressona-url","URL do Ressoa","http://127.0.0.1:8768");
            var ressona=new RessonaClient(url);
            ensureRessona(ressona);
            var catalog=ressona.engines();
            var availableEngines=catalog.stream().filter(item->item.has("available") && item.get("available").getAsBoolean()).toList();
            if(availableEngines.isEmpty()) throw new IOException("Nenhum motor do Ressoa está instalado e pronto para uso.");
            System.out.println("Motores disponíveis:");
            for(int i=0;i<availableEngines.size();i++) {
                var item=availableEngines.get(i);
                System.out.printf("  %d) %s [%s]%n",i+1,item.get("name").getAsString(),item.get("id").getAsString());
            }
            for(var item:catalog) if(!availableEngines.contains(item))
                System.out.printf("  • %s [%s] — indisponível (%s)%n",item.get("name").getAsString(),
                        item.get("id").getAsString(),item.get("status").getAsString());
            var choiceHistory=new CliRessonaHistory();
            String rememberedEngine=choiceHistory.engine();
            String preferredEngine=availableEngines.stream().anyMatch(item->rememberedEngine.equals(item.get("id").getAsString()))
                    ? rememberedEngine : availableEngines.stream().anyMatch(item->"supertonic3".equals(item.get("id").getAsString()))
                    ? "supertonic3" : availableEngines.getFirst().get("id").getAsString();
            String engine=select("--engine","Motor (número ou ID)",preferredEngine,availableEngines);
            var voices=ressona.voices(engine);
            if(voices.isEmpty()) throw new IOException("O Ressoa não tem voz disponível para o motor " + engine + ". Instale-o e configure uma voz no Ressoa.");
            System.out.println("Vozes disponíveis para "+engine+":");
            for(int i=0;i<voices.size();i++) {
                var item=voices.get(i);
                System.out.printf("  %d) %s [%s]%n",i+1,item.get("name").getAsString(),item.get("id").getAsString());
            }
            String rememberedVoice=choiceHistory.voice(engine);
            String preferredVoice=voices.stream().anyMatch(item->rememberedVoice.equals(item.get("id").getAsString()))
                    ? rememberedVoice : voices.stream().filter(item->item.has("is_default") && item.get("is_default").getAsBoolean())
                    .findFirst().orElse(voices.getFirst()).get("id").getAsString();
            String voice=select("--voice","Voz (número ou ID)",preferredVoice,voices);
            choiceHistory.remember(engine,voice);
            var selectedVoice=voices.stream().filter(item->voice.equals(item.get("id").getAsString())).findFirst().orElseThrow();
            String referenceText="";
            if(engine.equals("omnivoice") && selectedVoice.has("kind") && selectedVoice.get("kind").getAsString().equals("cloned")) {
                referenceText=value("--reference-text","Transcrição exata do áudio de referência para esta voz","");
                if(referenceText.isBlank()) throw new IOException("OmniVoice exige a transcrição exata do áudio de referência.");
            }
            Path projects=Path.of(value("--projects-dir","Pasta dos projetos",Path.of("projects").toAbsolutePath().toString())).toAbsolutePath();
            Path videos=Path.of(value("--videos-dir","Pasta dos vídeos",Path.of("videos").toAbsolutePath().toString())).toAbsolutePath();
            Path folder=uniqueFolder(projects,name);
            Files.createDirectories(folder);
            Path manifest=folder.resolve(folder.getFileName()+".mvideo.json");
            Path assets=folder.resolve(manifest.getFileName()+".assets");
            Files.createDirectories(assets);
            if(imageDirectory!=null) {
                for(int i=0;i<project.clips.size();i++) {
                    var clip=project.clips.get(i);
                    if(clip.image.isBlank()) continue;
                    Path source=Path.of(clip.image);
                    Path target=assets.resolve(String.format(Locale.ROOT,"scene-%03d%s",i+1,extension(source)));
                    Files.copy(source,target);
                    clip.image=target.toString();
                }
            }
            project.save(manifest);
            if(!project.avatarDirectory.isBlank()) new CliAvatarHistory().remember(Path.of(project.avatarDirectory));
            if(imageDirectory==null) {
                String key=settings.apiKey.isBlank()?System.getenv().getOrDefault("OPENAI_API_KEY",""):settings.apiKey;
                new ImageGenerationJob(OpenAiImageClient.fromSessionKey(key)).run(project,assets,
                        (index,file)->{ project.clips.get(index).image=file.toString(); save(project,manifest); },System.out::println);
            }
            project.save(manifest);

            var pieces=new ArrayList<Path>();
            String ffprobe=ExternalTools.command(settings.ffprobeExecutable,"ffprobe");
            for(int i=0;i<project.clips.size();i++) {
                Path wav=assets.resolve(String.format(Locale.ROOT,"narracao-%03d.wav",i+1));
                System.out.printf("Narrando cena %d/%d…%n",i+1,project.clips.size());
                ressona.synthesize(project.clips.get(i).text,engine,voice,referenceText,wav,System.out::println);
                project.clips.get(i).duration=probeDuration(ffprobe,wav);
                pieces.add(wav);
                project.save(manifest);
            }
            Path narration=assets.resolve("narracao-completa.wav");
            concatAudio(ExternalTools.command(settings.ffmpegExecutable,"ffmpeg"),pieces,narration);
            project.narration.file=narration.toString();
            if(applySounds) {
                var result=SoundApplicator.apply(project,Path.of(settings.soundsDirectory),settings.soundEffectPercent,
                        ExternalTools.command(settings.ffmpegExecutable,"ffmpeg"));
                System.out.println("Sons aplicados: "+result.addedCount());
            }
            project.save(manifest);
            Files.createDirectories(videos);
            Path output=uniqueFile(videos,folder.getFileName().toString(),".mp4");
            System.out.println("Exportando "+output+"…");
            new VideoExporter(settings.ffmpegExecutable,settings.ffprobeExecutable).export(project,output,System.out::println);
            System.out.println("Vídeo pronto: "+output);
            System.out.println("Projeto editável: "+manifest);
        } finally {
            if(startedRessona!=null) startedRessona.destroy();
        }
    }

    static void assignImages(VideoProject project,Path directory) throws IOException {
        List<Path> files;
        try(var stream=Files.list(directory)) {
            files=stream.filter(Files::isRegularFile)
                    .filter(file->file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|gif)"))
                    .sorted(Comparator.comparing(path->path.getFileName().toString())).toList();
        }
        if(files.size()>project.clips.size()) throw new IOException("Há mais imagens que cenas. Numere e deixe somente as imagens deste vídeo na pasta.");
        for(int i=0;i<files.size();i++) project.clips.get(i).image=files.get(i).toAbsolutePath().toString();
        for(int i=0;i<project.clips.size();i++)
            if(project.clips.get(i).image.isBlank() && project.clips.get(i).avatar.isBlank())
                throw new IOException("Falta imagem para a cena "+(i+1)+". Uma cena sem imagem precisa de uma marcação de avatar.");
    }

    private void ensureRessona(RessonaClient client) throws Exception {
        if(client.available()) return;
        String home=value("--ressona-home","Ressoa não está ativo. Pasta da instalação (com run-linux.sh)",System.getenv().getOrDefault("RESSONA_HOME",""));
        if(home.isBlank()) throw new IOException("Inicie o Ressoa ou informe RESSONA_HOME / --ressona-home.");
        Path launcher=directory(home,"Ressoa").resolve("run-linux.sh");
        if(!Files.isRegularFile(launcher)) throw new IOException("run-linux.sh não encontrado em "+home);
        Path log=Files.createTempFile("megalodonte-ressona-",".log");
        startedRessona=new ProcessBuilder("bash",launcher.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        for(int i=0;i<30;i++) {
            if(client.available()) return;
            if(!startedRessona.isAlive()) throw new IOException("Ressoa não iniciou; veja "+log);
            Thread.sleep(1000);
        }
        throw new IOException("Ressoa não respondeu em 30 segundos; veja "+log);
    }

    static double probeDuration(String ffprobe,Path audio) throws IOException,InterruptedException {
        var process=new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",audio.toString())
                .redirectErrorStream(true).start();
        String result=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip();
        if(process.waitFor()!=0) throw new IOException("FFprobe não conseguiu medir "+audio+": "+result);
        try {
            double duration=Double.parseDouble(result);
            if(!Double.isFinite(duration) || duration<.1 || duration>3600) throw new NumberFormatException();
            return duration;
        } catch(NumberFormatException error) { throw new IOException("Duração inválida da cena narrada: "+result,error); }
    }

    static void concatAudio(String ffmpeg,List<Path> pieces,Path output) throws IOException,InterruptedException {
        Path list=Files.createTempFile(output.getParent(),".narracao-",".ffconcat");
        try {
            var lines=new StringBuilder("ffconcat version 1.0\n");
            for(Path piece:pieces) lines.append("file '").append(piece.getFileName()).append("'\n");
            Files.writeString(list,lines);
            var process=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","error","-y","-safe","0","-f","concat","-i",list.toString(),"-c:a","pcm_s16le",output.toString())
                    .directory(output.getParent().toFile()).redirectErrorStream(true).start();
            String detail=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            if(process.waitFor()!=0) throw new IOException("FFmpeg não conseguiu juntar a narração: "+detail);
        } finally { Files.deleteIfExists(list); }
    }

    private String readScript() throws IOException {
        System.out.println("Cole o roteiro, com uma linha vazia entre cenas. Termine com uma linha contendo apenas ::fim");
        var script=new StringBuilder();
        String line;
        while((line=input.readLine())!=null && !isScriptTerminator(line)) script.append(line).append('\n');
        if(script.isEmpty()) throw new IOException("Roteiro vazio.");
        return script.toString();
    }

    static boolean isScriptTerminator(String line) {
        return line != null && line.strip().equalsIgnoreCase("::fim");
    }

    private String avatarDirectory() throws IOException {
        if(options.containsKey("--avatar-dir")) return options.get("--avatar-dir").strip();
        var history=new CliAvatarHistory().lastDirectory();
        if(history.isPresent()) {
            Path previous=history.get();
            if(Files.isDirectory(previous) && Files.isReadable(previous)) {
                while(true) {
                    String answer=value("--reuse-avatar-dir","Reaproveitar a última pasta de avatares ("+previous+")? (s/n)","s");
                    if(answer.equalsIgnoreCase("s")||answer.equalsIgnoreCase("sim")) return previous.toString();
                    if(answer.equalsIgnoreCase("n")||answer.equalsIgnoreCase("nao")||answer.equalsIgnoreCase("não")) break;
                    System.out.println("Responda s ou n.");
                }
            } else System.out.println("A última pasta de avatares não está mais disponível: "+previous);
        }
        return value("--avatar-dir","Pasta das imagens do avatar (vazio se não usar)","");
    }

    private String value(String flag,String label,String defaultValue) throws IOException {
        if(options.containsKey(flag)) return options.get(flag).strip();
        System.out.print(label+(defaultValue.isBlank()?"": " ["+defaultValue+"]")+": ");
        String answer=input.readLine();
        if(answer==null) throw new EOFException("Entrada encerrada antes de preencher "+label);
        return answer.isBlank()?defaultValue:answer.strip();
    }

    private String select(String flag,String label,String defaultValue,List<JsonObject> choices) throws IOException {
        while(true) {
            String answer=value(flag,label,defaultValue);
            if(answer.matches("[0-9]+")) {
                try {
                    int index=Integer.parseInt(answer)-1;
                    if(index>=0 && index<choices.size()) return choices.get(index).get("id").getAsString();
                } catch(NumberFormatException ignored) { }
            }
            if(choices.stream().anyMatch(item->answer.equals(item.get("id").getAsString()))) return answer;
            if(options.containsKey(flag)) throw new IOException("Escolha indisponível para "+label+": "+answer);
            System.out.println("Escolha um número ou ID exibido na lista.");
        }
    }

    private static Map<String,String> parse(String[] args) {
        var options=new LinkedHashMap<String,String>();
        var allowed=Set.of("--name","--script-file","--avatar-dir","--images-dir","--effect","--sounds","--projects-dir","--videos-dir","--ressona-url","--ressona-home","--engine","--voice","--reference-text");
        for(int i=0;i<args.length;i++) {
            String arg=args[i];
            if(arg.equals("--help")||arg.equals("--dry-run")) { options.put(arg,""); continue; }
            if(!allowed.contains(arg)||i+1>=args.length) throw new IllegalArgumentException("Opção inválida: "+arg+". Use --help.");
            options.put(arg,args[++i]);
        }
        return options;
    }

    private static Path directory(String value,String label) throws IOException {
        Path path=Path.of(value).toAbsolutePath().normalize();
        if(!Files.isDirectory(path)||!Files.isReadable(path)) throw new IOException("Pasta de "+label+" não encontrada: "+path);
        return path;
    }
    private static String slug(String text) {
        String value=java.text.Normalizer.normalize(text.strip().toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").replaceAll("[^a-z0-9_-]+","-").replaceAll("^-+|-+$","");
        if(value.isBlank()) throw new IllegalArgumentException("Informe um nome válido para o vídeo.");
        return value;
    }
    private static String extension(Path path) {
        String name=path.getFileName().toString();
        return name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
    private static Path uniqueFolder(Path parent,String name) {
        Path candidate=parent.resolve(name);
        for(int i=2;Files.exists(candidate);i++) candidate=parent.resolve(name+"-"+i);
        return candidate;
    }
    private static Path uniqueFile(Path parent,String name,String extension) {
        Path candidate=parent.resolve(name+extension);
        for(int i=2;Files.exists(candidate);i++) candidate=parent.resolve(name+"-"+i+extension);
        return candidate;
    }
    private static void save(VideoProject project,Path manifest) {
        try { project.save(manifest); }
        catch(IOException error) { throw new UncheckedIOException(error); }
    }
    private static void help() {
        System.out.println("Uso: ./video-cli [opções]\n"
                +"Sem opções, abre o assistente interativo. Termine o roteiro colado com ::fim.\n"
                +"--script-file ARQUIVO --name NOME --avatar-dir PASTA --images-dir PASTA\n"
                +"--effect none|smooth|particles|shake --sounds s|n\n"
                +"--ressona-url URL --ressona-home PASTA --engine MOTOR --voice ID\n"
                +"--reference-text TEXTO (para voz clonada do OmniVoice)\n"
                +"--projects-dir PASTA --videos-dir PASTA --dry-run");
    }
}
