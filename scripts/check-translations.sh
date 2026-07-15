#!/usr/bin/env bash
#
# check-translations.sh —— 多语言 strings.xml 完整性检查
#
# 以 app/src/main/res/values/strings.xml（基准）的 key 集合为准，
# 对比各语言目录（values-*/strings.xml）的 key，报告缺失/多余的 key。
#
# 用法：
#   bash scripts/check-translations.sh
#
# 退出码：0 = 全部一致；1 = 存在缺失（适合接 CI 失败）。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES_DIR="$SCRIPT_DIR/app/src/main/res"
BASE="$RES_DIR/values/strings.xml"

if [[ ! -f "$BASE" ]]; then
    echo "❌ 基准文件未找到: $BASE" >&2
    exit 2
fi

# 提取 key 集合（仅 string 项，不含 string-array/plurals）
extract_keys() {
    grep -o '<string name="[^"]*"' "$1" | sed -E 's/.*name="([^"]*)".*/\1/' | sort -u
}

BASE_KEYS=$(extract_keys "$BASE")
BASE_COUNT=$(echo "$BASE_KEYS" | grep -c .)

echo "📋 基准: values/strings.xml — ${BASE_COUNT} 个 key"
echo "────────────────────────────────────────────"

STATUS=0
TMP_BASE=$(mktemp); echo "$BASE_KEYS" > "$TMP_BASE"
trap 'rm -f "$TMP_BASE" "$TMP_DIR"/*.keys 2>/dev/null' EXIT
TMP_DIR=$(mktemp -d)

for dir in "$RES_DIR"/values-*; do
    [[ -d "$dir" ]] || continue
    lang=$(basename "$dir" | sed 's/values-//')
    file="$dir/strings.xml"
    [[ -f "$file" ]] || { echo "⚠️  跳过 $lang（无 strings.xml）"; continue; }

    LOC_KEYS=$(extract_keys "$file")
    LOC_COUNT=$(echo "$LOC_KEYS" | grep -c .)
    TMP_LOC="$TMP_DIR/$lang.keys"; echo "$LOC_KEYS" > "$TMP_LOC"

    MISSING=$(comm -23 "$TMP_BASE" "$TMP_LOC")
    EXTRA=$(comm -13 "$TMP_BASE" "$TMP_LOC")
    MISSING_N=$(echo "$MISSING" | grep -c .)
    EXTRA_N=$(echo "$EXTRA" | grep -c .)

    if [[ "$MISSING_N" -gt 0 || "$EXTRA_N" -gt 0 ]]; then
        STATUS=1
        echo "❌ $lang ($LOC_COUNT/$BASE_COUNT)"
        [[ "$MISSING_N" -gt 0 ]] && echo "   缺失 $MISSING_N 个: $(echo "$MISSING" | paste -sd' ' -)"
        [[ "$EXTRA_N" -gt 0 ]] && echo "   多余 $EXTRA_N 个: $(echo "$EXTRA" | paste -sd' ' -)"
    else
        echo "✅ $lang ($LOC_COUNT/$BASE_COUNT)"
    fi
done

echo "────────────────────────────────────────────"
if [[ $STATUS -eq 0 ]]; then
    echo "✅ 所有语言 key 与基准一致"
else
    echo "❌ 存在缺失/多余 key（详见上方）。请补全翻译或同步基准。"
fi
exit $STATUS
