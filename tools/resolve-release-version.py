"""解析发布版本：手动输入优先；push 时基于已有 preview 标签自动递增。"""
import re
import subprocess
import sys

PREVIEW_TAG = re.compile(r'^vpreview-(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$')


def next_preview(tags):
    """按已有 preview 标签取最大版本号，递增次版本；99 进位到主版本。"""
    highest = (0, 0)
    for tag in tags or ():
        match = PREVIEW_TAG.match(tag)
        if match is None:
            continue
        major, minor = int(match.group(1)), int(match.group(2))
        if major <= 99 and minor <= 99:
            highest = max(highest, (major, minor))
    major, minor = highest
    if minor >= 99:
        major, minor = major + 1, 0
    else:
        minor += 1
    if major > 99:
        raise SystemExit('preview 版本号超出范围，请改用正式版本发布')
    return 'preview-%d.%d' % (major, minor)


def resolve(input_version, tags):
    """手动发布尊重输入；自动发布永远计算下一个未被占用的 preview 版本。"""
    if input_version:
        return input_version
    version = next_preview(tags)
    while 'v' + version in (tags or ()):
        major, minor = map(int, version[len('preview-'):].split('.'))
        version = next_preview(['vpreview-%d.%d' % (major, minor)])
    return version


def main():
    input_version = sys.argv[1] if len(sys.argv) > 1 else ''
    if input_version:
        version = input_version
    else:
        tags = subprocess.check_output(
            ['git', 'tag', '--list', 'vpreview-*'], text=True,
        ).splitlines()
        version = resolve('', tags)
    print('RELEASE_VERSION=' + version)


if __name__ == '__main__':
    main()
