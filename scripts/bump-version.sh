#!/usr/bin/env bash
#
# bump-version.sh —— 把 version.properties 中的 PATCH 版本号 +1
#
# 用法：
#   bash scripts/bump-version.sh [--commit]
#
# --commit 时会自动 git commit + push 回主仓库（用于 GitHub Actions 自增场景）
#
# 版本号映射：
#   version.properties 中 VERSION_MAJOR / VERSION_MINOR / VERSION_PATCH
#   → versionName = "MAJOR.MINOR.PATCH"
#   → versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/version.properties"

if [[ ! -f "$VERSION_FILE" ]]; then
    echo "❌ version.properties not found at $VERSION_FILE" >&2
    exit 1
fi

# 读取当前版本
CUR_MAJOR=$(grep '^VERSION_MAJOR=' "$VERSION_FILE" | cut -d= -f2)
CUR_MINOR=$(grep '^VERSION_MINOR=' "$VERSION_FILE" | cut -d= -f2)
CUR_PATCH=$(grep '^VERSION_PATCH=' "$VERSION_FILE" | cut -d= -f2)

NEW_PATCH=$((CUR_PATCH + 1))
NEW_VERSION_NAME="${CUR_MAJOR}.${CUR_MINOR}.${NEW_PATCH}"
NEW_VERSION_CODE=$((CUR_MAJOR * 10000 + CUR_MINOR * 100 + NEW_PATCH))

echo "🔖 版本号自增: ${CUR_MAJOR}.${CUR_MINOR}.${CUR_PATCH} → ${NEW_VERSION_NAME} (code=${NEW_VERSION_CODE})"

# 写回文件
cat > "$VERSION_FILE" <<EOF
# 版本号单一来源。由 scripts/bump-version.sh 自动维护。
# 工作流每次 push 到 main 时会调用 bump-version.sh 把 PATCH +1 并提交回仓库。
# 格式：MAJOR.MINOR.PATCH，对应 Android versionName。
# versionCode 自动 = MAJOR*10000 + MINOR*100 + PATCH
VERSION_MAJOR=${CUR_MAJOR}
VERSION_MINOR=${CUR_MINOR}
VERSION_PATCH=${NEW_PATCH}
EOF

cat "$VERSION_FILE"

# 输出 GitHub Actions 可用的环境变量
if [[ -n "${GITHUB_ENV:-}" ]]; then
    {
        echo "VERSION_NAME=${NEW_VERSION_NAME}"
        echo "VERSION_CODE=${NEW_VERSION_CODE}"
        echo "VERSION_MAJOR=${CUR_MAJOR}"
        echo "VERSION_MINOR=${CUR_MINOR}"
        echo "VERSION_PATCH=${NEW_PATCH}"
    } >> "$GITHUB_ENV"
fi

# 可选：自动 commit + push
if [[ "${1:-}" == "--commit" ]]; then
    cd "$ROOT_DIR"
    git config user.name  "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git add version.properties
    git commit -m "chore(release): bump version to ${NEW_VERSION_NAME} [skip ci]"
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        git push "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" HEAD:"${GITHUB_REF_NAME:-main}"
    else
        git push
    fi
    echo "✅ 版本号已提交回仓库"
fi
