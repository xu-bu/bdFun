import importlib.util
import os
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('agent', 'tools/release-notes-agent.py')
agent = importlib.util.module_from_spec(spec)
spec.loader.exec_module(agent)

resolver_spec = importlib.util.spec_from_file_location('resolver', 'tools/resolve-release-version.py')
resolver = importlib.util.module_from_spec(resolver_spec)
resolver_spec.loader.exec_module(resolver)

class ReleaseNotesTest(unittest.TestCase):
    def test_manual_notes_never_call_api(self):
        with patch.dict(os.environ, {'RELEASE_NOTES': '- 新增 唱片控件'}, clear=True), patch.object(agent, 'git') as git:
            self.assertEqual('- 新增 唱片控件', agent.generate())
            git.assert_not_called()

    def test_missing_key_fails_explicitly(self):
        with patch.dict(os.environ, {'GENERATE_NOTES': 'true'}, clear=True), patch.object(agent, 'git', side_effect=['vpreview-5.0', '新增 更新检查']):
            with self.assertRaisesRegex(SystemExit, 'DEEPSEEK_API_KEY'):
                agent.generate()

    def test_empty_changes_are_not_invented(self):
        with patch.dict(os.environ, {'GENERATE_NOTES': 'true'}, clear=True), patch.object(agent, 'git', side_effect=['vpreview-5.0', '']):
            with self.assertRaisesRegex(SystemExit, '没有提交'):
                agent.generate()

    def test_auto_notes_use_commit_subjects_without_api(self):
        with patch.dict(os.environ, {'AUTO_NOTES': 'true'}, clear=True), \
             patch.object(agent, 'git', side_effect=['vpreview-7.4', 'feat(cache): 新增左滑删除\nfix: 修复重试']) as git:
            self.assertEqual('- 新增 左滑删除\n- 修复 重试', agent.generate())
            git.assert_called_with('log', '--no-merges', '--format=%s', 'vpreview-7.4..HEAD')

    def test_auto_notes_reject_empty_changes(self):
        with patch.dict(os.environ, {'AUTO_NOTES': 'true'}, clear=True), \
             patch.object(agent, 'git', side_effect=['vpreview-7.4', '']):
            with self.assertRaisesRegex(SystemExit, '没有提交'):
                agent.generate()

    def test_next_preview_increments_and_carries(self):
        self.assertEqual('preview-7.5', resolver.resolve('', ['vpreview-7.4', 'vpreview-6.9']))
        self.assertEqual('preview-8.0', resolver.resolve('', ['vpreview-7.99']))
        self.assertEqual('preview-0.1', resolver.resolve('', []))
        self.assertEqual('1.2.3', resolver.resolve('1.2.3', ['vpreview-7.4']))

    def test_auto_version_skips_existing_tag(self):
        self.assertEqual('preview-7.6', resolver.resolve('', ['vpreview-7.4', 'vpreview-7.5']))

if __name__ == '__main__':
    unittest.main()
