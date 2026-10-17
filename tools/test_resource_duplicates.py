import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('check_resource_duplicates', Path(__file__).with_name('check-resource-duplicates.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class DuplicateResourcesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write(self, source, qualifier, file, contents):
        path = self.root/source/qualifier/file
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('<resources>'+contents+'</resources>')

    def test_same_file_duplicate_with_different_translation_attribute(self):
        self.write('mobile', 'values', 'strings.xml', '<string name="done">Done</string><string name="done" translatable="false">Done</string>')
        self.assertEqual(1, len(checker.duplicates([self.root/'mobile'])))

    def test_duplicate_across_files_in_same_configuration(self):
        for file in ('first.xml', 'second.xml'):
            self.write('mobile', 'values', file, '<string name="done">Done</string>')
        self.assertEqual(1, len(checker.duplicates([self.root/'mobile'])))

    def test_translations_night_values_and_flavor_overrides_are_separate(self):
        for source in ('main', 'mobile'):
            for qualifier in ('values', 'values-es', 'values-night'):
                self.write(source, qualifier, 'strings.xml', '<string name="done">Done</string>')
        self.assertEqual([], checker.duplicates([self.root/'main', self.root/'mobile']))

    def test_array_types_share_one_resource_namespace(self):
        self.write('mobile', 'values', 'arrays.xml', '<string-array name="choices"><item>A</item></string-array><integer-array name="choices"><item>1</item></integer-array>')
        self.assertEqual(1, len(checker.duplicates([self.root/'mobile'])))

    def test_metadata_ids_styleables_and_nested_attributes_do_not_conflict(self):
        self.write('mobile', 'values', 'attrs.xml', '<public type="string" name="done"/><string name="done">Done</string><item type="id" name="row"/><item type="id" name="row"/><declare-styleable name="Widget"><attr name="label" format="string"/></declare-styleable><declare-styleable name="Widget"><attr name="size" format="integer"/></declare-styleable>')
        self.assertEqual([], checker.duplicates([self.root/'mobile']))


if __name__ == '__main__':
    unittest.main()
