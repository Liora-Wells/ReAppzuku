"""检查 values-zh-rCN/strings.xml 中缺失的翻译条目。

对比 values/strings.xml（默认英文）和 values-zh-rCN/strings.xml（简体中文），
列出英文有但中文缺失的字符串 key。
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def extract_translatable_keys(xml_path: Path) -> set[str]:
    """从 strings.xml 中提取所有可翻译的字符串 key。

    排除 translatable="false" 的条目。
    """
    tree = ET.parse(xml_path)
    root = tree.getroot()

    keys = set()
    for elem in root:
        tag = elem.tag
        if tag not in ("string", "plurals"):
            continue
        if elem.get("translatable", "").lower() == "false":
            continue
        name = elem.get("name")
        if name:
            keys.add(name)

    return keys


def main() -> int:
    project_root = Path(__file__).resolve().parent.parent
    base_path = project_root / "app" / "src" / "main" / "res"

    en_path = base_path / "values" / "strings.xml"
    zh_path = base_path / "values-zh-rCN" / "strings.xml"

    if not en_path.exists():
        print(f"错误: 找不到英文 strings.xml: {en_path}")
        return 1

    if not zh_path.exists():
        print(f"错误: 找不到中文 strings.xml: {zh_path}")
        return 1

    en_keys = extract_translatable_keys(en_path)
    zh_keys = extract_translatable_keys(zh_path)

    missing = en_keys - zh_keys

    if not missing:
        print("所有字符串均已翻译。")
        return 0

    print(f"缺失翻译的字符串 ({len(missing)} 条):")
    for key in sorted(missing):
        print(f"  - {key}")

    return 0


if __name__ == "__main__":
    sys.exit(main())