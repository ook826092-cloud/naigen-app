#!/usr/bin/env bash
#
# bump-version.sh —— 双层版本号自增
#
# 格式：BUILD_NUMBER / MAJOR.MINOR.PATCH
#   - BUILD_NUMBER: 每次调用 +1，永不动（单调递增的构建号）
#   - MAJOR.MINOR.PATCH: SemVer
#     PATCH +1, 逢 9 进 1 到 MINOR (2.0.9 → 2.1.0)
#     MINOR 逢 9 进 1 到 MAJOR (2.9.9 → 3.0.0)
#
# 用法：
#   bash scripts/bump-version.sh [--commit]
#
# --commit 时会 git commit + push 回主仓库（GitHub Actions 用）
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/version.properties"

if [[ ! -f "$VERSION_FILE" ]]; then
    echo "❌ version.properties not found at $VERSION_FILE" >&2
    exit 1
fi

# 用 awk 读取，避免 source 解析 properties 出问题
# 兼容旧字段名（VERSION_MAJOR 等）和新字段名（MAJOR 等），找不到时给默认值
read_field() {
    local key="$1"
    local default="$2"
    # 先找新字段名，找不到再找旧字段名
    local val
    val=$(awk -F= "/^${key}=/{print \$2}" "$VERSION_FILE")
    if [[ -z "$val" ]]; then
        val=$(awk -F= "/^VERSION_${key}=/{print \$2}" "$VERSION_FILE")
    fi
    if [[ -z "$val" ]]; then
        val="$default"
    fi
    echo "$val"
}

CUR_BUILD=$(read_field BUILD_NUMBER 1)
CUR_MAJOR=$(read_field MAJOR 1)
CUR_MINOR=$(read_field MINOR 0)
CUR_PATCH=$(read_field PATCH 0)

NEW_BUILD=$((CUR_BUILD + 1))

# SemVer 逢 9 进位
NEW_PATCH=$((CUR_PATCH + 1))
NEW_MINOR=$CUR_MINOR
NEW_MAJOR=$CUR_MAJOR
if [[ $NEW_PATCH -gt 9 ]]; then
    NEW_PATCH=0
    NEW_MINOR=$((CUR_MINOR + 1))
    if [[ $NEW_MINOR -gt 9 ]]; then
        NEW_MINOR=0
        NEW_MAJOR=$((CUR_MAJOR + 1))
    fi
fi

NEW_SEMVER="${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}"
NEW_VERSION_NAME="${NEW_BUILD}/${NEW_SEMVER}"
NEW_VERSION_CODE=$NEW_BUILD

echo "🔖 版本自增: ${CUR_BUILD}/${CUR_MAJOR}.${CUR_MINOR}.${CUR_PATCH} → ${NEW_VERSION_NAME}"

# 写回文件
cat > "$VERSION_FILE" <<EOF
# 版本号单一来源。由 scripts/bump-version.sh 自动维护。
#
# 双层版本号格式：build_number / semver
#   - BUILD_NUMBER: 累计构建次数，每次 bump 都 +1，单调递增永不动
#   - MAJOR.MINOR.PATCH: 标准 SemVer
#     PATCH 每次 bump +1，逢 9 进 1 到 MINOR（2.0.9 → 2.1.0）
#     MINOR 逢 9 进 1 到 MAJOR（2.9.9 → 3.0.0）
#
# 显示示例（versionName）：
#   第 165 次构建 + 2.1.5  →  "165/2.1.5"
#   下一次构建：166/2.1.6
#   再下一次：167/2.1.7
#   ...到 2.1.9 后下一次：168/2.2.0
#
# Android versionCode 直接用 BUILD_NUMBER（保证单调递增）
#
# 工作流 push 触发时会调 bump-version.sh 把 BUILD_NUMBER +1 并按逢 9 进位规则更新 SemVer
BUILD_NUMBER=${NEW_BUILD}
MAJOR=${NEW_MAJOR}
MINOR=${NEW_MINOR}
PATCH=${NEW_PATCH}
EOF

cat "$VERSION_FILE"

# 输出给 GitHub Actions 用
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
        echo "VERSION_NAME=${NEW_VERSION_NAME}"
        echo "VERSION_CODE=${NEW_VERSION_CODE}"
        echo "BUILD_NUMBER=${NEW_BUILD}"
        echo "SEMVER=${NEW_SEMVER}"
        echo "MAJOR=${NEW_MAJOR}"
        echo "MINOR=${NEW_MINOR}"
        echo "PATCH=${NEW_PATCH}"
    } >> "$GITHUB_OUTPUT"
fi
if [[ -n "${GITHUB_ENV:-}" ]]; then
    {
        echo "VERSION_NAME=${NEW_VERSION_NAME}"
        echo "VERSION_CODE=${NEW_VERSION_CODE}"
        echo "BUILD_NUMBER=${NEW_BUILD}"
        echo "SEMVER=${NEW_SEMVER}"
    } >> "$GITHUB_ENV"
fi

# 可选：commit + push
if [[ "${1:-}" == "--commit" ]]; then
    cd "$ROOT_DIR"
    git config user.name  "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git add version.properties
    git commit -m "chore(release): bump to ${NEW_VERSION_NAME} [skip ci]"
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        git push "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" HEAD:"${GITHUB_REF_NAME:-main}"
    else
        git push
    fi
    echo "✅ 版本号已提交回仓库"
fi
