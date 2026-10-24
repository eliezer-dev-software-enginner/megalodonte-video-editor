import unittest
from sync_narration import align


class AlignmentTest(unittest.TestCase):
    def test_pauses_define_cuts_and_audio_end_is_preserved(self):
        clips = [{'text': 'Bom dia, cidade.'}, {'text': 'A energia elétrica chegou.'}]
        texts = ['Bom', 'dia', 'cidade', 'A', 'energia', 'elétrica', 'chegou']
        starts = [.6, 1, 1.4, 3.2, 3.5, 3.8, 4.1]
        words = [dict(text=text, start=start, end=start+.3) for text, start in zip(texts, starts)]
        result = align(clips, words, 5.019)
        self.assertEqual(1, result['coverage'])
        self.assertEqual(round((1.7+.2)*30)/30, result['scenes'][1]['start'])
        self.assertEqual('after_speech', result['scenes'][1]['cutMode'])
        self.assertAlmostEqual(1.5, result['scenes'][1]['precedingPause'])
        self.assertEqual(0, result['scenes'][0]['start'])
        self.assertGreaterEqual(result['timelineDuration'], 5.019)
        self.assertLess(result['timelineDuration']-5.019, 1/30)
        self.assertAlmostEqual(result['timelineDuration'], sum(s['duration'] for s in result['scenes']))
        self.assertAlmostEqual(.6, result['leadingSilence'])
        self.assertAlmostEqual(.619, result['trailingSilence'])

    def test_short_pause_keeps_midpoint_cut(self):
        clips = [{'text': 'Bom dia cidade'}, {'text': 'A energia chegou'}]
        texts = 'Bom dia cidade A energia chegou'.split()
        starts = [.1, .4, .7, 1.3, 1.6, 1.9]
        words = [dict(text=text, start=start, end=start+.2) for text, start in zip(texts, starts)]
        result = align(clips, words, 2.3)
        self.assertEqual(round(((.9+1.3)/2)*30)/30, result['scenes'][1]['start'])
        self.assertEqual('midpoint', result['scenes'][1]['cutMode'])

    def test_pause_threshold_can_be_configured(self):
        clips = [{'text': 'Bom dia cidade'}, {'text': 'A energia chegou'}]
        texts = 'Bom dia cidade A energia chegou'.split()
        starts = [.1, .4, .7, 2.4, 2.7, 3.0]
        words = [dict(text=text, start=start, end=start+.2) for text, start in zip(texts, starts)]
        default = align(clips, words, 3.4)
        slower = align(clips, words, 3.4, pause_cut_threshold=2)
        self.assertEqual('after_speech', default['scenes'][1]['cutMode'])
        self.assertEqual('midpoint', slower['scenes'][1]['cutMode'])
        self.assertGreater(slower['scenes'][1]['start'], default['scenes'][1]['start'])
        with self.assertRaises(ValueError): align(clips, words, 3.4, pause_cut_threshold=float('nan'))

    def test_long_pause_inside_one_scene_does_not_create_cut(self):
        clips = [{'text': 'Bom dia cidade energia chegou hoje'}]
        texts = 'Bom dia cidade energia chegou hoje'.split()
        starts = [.1, .4, .7, 4.0, 4.3, 4.6]
        words = [dict(text=text, start=start, end=start+.2) for text, start in zip(texts, starts)]
        result = align(clips, words, 5.2)
        self.assertEqual(1, len(result['scenes']))
        self.assertAlmostEqual(5.2, result['scenes'][0]['duration'], delta=1/30)

    def test_short_improvised_lead_in_keeps_reliable_scene_anchor(self):
        clips=[{'text':'Fim anterior seguro.'},
               {'text':'E aqui entra uma parte importante dessa iniciativa para começar um projeto menor com etapas claras e decisões práticas para testar publicar aprender com uso real ao longo do tempo.'}]
        spoken=['Fim','anterior','seguro','que','entre','uma','parte','importante','nessa','iniciativa','para','começar','um','projeto','menor','com','etapas','claras','e','decisões','práticas','para','testar','publicar','aprender','com','uso','real','ao','longo','do','tempo']
        words=[dict(text=word,start=.4*i,end=.4*i+.3) for i,word in enumerate(spoken)]
        result=align(clips,words,15.0)
        self.assertGreater(result['coverage'],.75)
        self.assertLess(result['scenes'][1]['start'],words[5]['start'])

    def test_truncated_scene_prefix_uses_clear_next_scene_anchor(self):
        clips=[{'text':'A expectativa é combinar competências para investigar novas maneiras importantes para os experimentos.'},
               {'text':'Mas é importante compreender em que estágio se encontra.'},
               {'text':'A pergunta continua aberta hoje.'}]
        spoken='A expectativa é combinar competências para investigar mas é importante compreender em que estágio se encontra a pergunta continua aberta hoje'.split()
        words=[dict(text=word,start=.5*i,end=.5*i+.3) for i,word in enumerate(spoken)]
        result=align(clips,words,11)
        self.assertTrue(result['scenes'][0]['partial'])
        self.assertFalse(result['scenes'][1]['partial'])
        self.assertGreater(result['scenes'][1]['start'],result['scenes'][0]['speechEnd'])

    def test_unanchored_partial_scene_is_rejected(self):
        clips=[{'text':'A expectativa é combinar essas competências para investigar novas maneiras diferentes e interessantes.'},
               {'text':'Mas é importante compreender em que estágio essa iniciativa realmente se encontra.'}]
        spoken='A expectativa é combinar essas competências para investigar outra frase sem relação alguma mas é importante compreender em que estágio essa iniciativa realmente se encontra'.split()
        words=[dict(text=word,start=.5*i,end=.5*i+.3) for i,word in enumerate(spoken)]
        with self.assertRaises(ValueError): align(clips,words,12)

    def test_unrelated_narration_is_rejected(self):
        with self.assertRaises(ValueError):
            align([{'text': 'O Google anunciou um acordo nuclear.'}],
                  [{'text': 'Minha bicicleta caiu na praça', 'start': 0, 'end': 2}], 3)

    def test_overlapping_scene_speech_is_rejected(self):
        words = [{'text': t, 'start': .5*i, 'end': .5*i+.3} for i, t in enumerate(['Bom','dia','cidade'])]
        words += [{'text': t, 'start': .5*i, 'end': .5*i+.3} for i, t in enumerate(['energia','nuclear','chegou'])]
        with self.assertRaises(ValueError):
            align([{'text': 'Bom dia cidade'}, {'text': 'energia nuclear chegou'}], words, 3)


if __name__ == '__main__':
    unittest.main()
