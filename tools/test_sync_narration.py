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
        self.assertEqual(round(((1.7+3.2)/2)*30)/30, result['scenes'][1]['start'])
        self.assertEqual(0, result['scenes'][0]['start'])
        self.assertGreaterEqual(result['timelineDuration'], 5.019)
        self.assertLess(result['timelineDuration']-5.019, 1/30)
        self.assertAlmostEqual(result['timelineDuration'], sum(s['duration'] for s in result['scenes']))

    def test_unrelated_narration_is_rejected(self):
        with self.assertRaises(ValueError):
            align([{'text': 'O Google anunciou um acordo nuclear.'}],
                  [{'text': 'Minha bicicleta caiu na praça', 'start': 0, 'end': 2}], 3)

    def test_overlapping_scene_speech_is_rejected(self):
        words = [{'text': t, 'start': .1*i, 'end': .1*i+.3} for i, t in enumerate(['Bom','dia','cidade'])]
        words += [{'text': t, 'start': .1*i, 'end': .1*i+.3} for i, t in enumerate(['energia','nuclear','chegou'])]
        with self.assertRaises(ValueError):
            align([{'text': 'Bom dia cidade'}, {'text': 'energia nuclear chegou'}], words, 3)


if __name__ == '__main__':
    unittest.main()
