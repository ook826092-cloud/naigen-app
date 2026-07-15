# Nai2API 文生图工具搭建指南

> **目标**：在全新 Linux 工作区中搭建 Nai2API 文生图工具链，并内置常用画风质量提示词，搭建后可直接出图。  
> **不含任何真实 API 密钥**，配置示例仅使用占位符，实际值只应保存在本地 `.env`。  
> **适用环境**：Ubuntu 22.04+（proot / WSL / 原生均可），ARM64 或 AMD64。

---

## 目录

1. [系统环境与基础依赖](#1-系统环境与基础依赖)
2. [镜像源配置（中国大陆）](#2-镜像源配置中国大陆)
3. [Python 虚拟环境](#3-python-虚拟环境)
4. [工作区目录结构](#4-工作区目录结构)
5. [Nai2API 工具链](#5-nai2api-工具链)
6. [环境变量配置](#6-环境变量配置)
7. [快速验证](#7-快速验证)
8. [附录](#8-附录)

---

## 1. 系统环境与基础依赖

### 1.1 系统要求

| 项目 | 最低要求 |
|---|---|
| OS | Ubuntu 22.04+ / Debian 12+ |
| Python | 3.12+ |
| 内存 | 1GB+ |
| 磁盘 | 2GB+（不含生成产物） |

### 1.2 安装系统包

```bash
sudo apt-get update
sudo apt-get install -y \
    python3 python3-venv python3-pip \
    git curl wget
```

### 1.3 验证

```bash
python3 --version    # 期望: 3.12.x
```

---

## 2. 镜像源配置（中国大陆）

如果工作区在中国大陆，建议配置镜像源加速下载。

```bash
# pip 清华源
mkdir -p ~/.config/pip
cat > ~/.config/pip/pip.conf << 'EOF'
[global]
index-url = https://pypi.tuna.tsinghua.edu.cn/simple
trusted-host = pypi.tuna.tsinghua.edu.cn
EOF
```

---

## 3. Python 虚拟环境

```bash
cd /workspace
python3 -m venv .venv
source .venv/bin/activate

pip install --upgrade pip
pip install requests python-dotenv Pillow rich
```

| 包 | 用途 |
|---|---|
| `requests` | HTTP 客户端，调用 Nai2API |
| `python-dotenv` | 加载 `.env` 中的环境变量 |
| `Pillow` | 图片处理（保存、缩放） |
| `rich` | 终端美化输出 |

---

## 4. 工作区目录结构

```bash
cd /workspace
mkdir -p image-gen-tool/outputs/nai/角色 \
         image-gen-tool/outputs/nai/场景 \
         image-gen-tool/outputs/nai/其他 \
         docs
```

```
/workspace/
├── image-gen-tool/          # 🎨 AI 图片生成
│   ├── config.json          # Provider 配置（使用占位符）
│   ├── api_handler.py       # Provider 实现（含 Nai2APIProvider）
│   ├── image_gen_api.py     # 核心 API 类
│   ├── nai_styles.json      # 社区风格库（29个）
│   └── outputs/             # 生成的图片
│       └── nai/             # Nai2API 产出
│           ├── 角色/         # 角色图
│           ├── 场景/         # 场景图
│           └── 其他/         # 参考图、素材
├── ai_media.py              # 📸 统一入口
├── docs/                    # 📄 文档
├── .venv/                   # Python 虚拟环境
└── .env                     # 环境变量
```

---

## 5. Nai2API 工具链

### 5.1 概述

Nai2API 是基于 NovelAI 的第三方代理 API，提供多种画风预设的文生图服务。  
采用 **Job 异步模式**：提交任务 → 轮询状态 → 下载图片。

| 属性 | 值 |
|---|---|
| API 地址 | `https://nai.sta1n.cn` |
| 计费方式 | 按点消耗 |
| 出图时间 | 约 30~40 秒 |

### 5.2 核心文件

| 文件 | 作用 |
|---|---|
| `image-gen-tool/api_handler.py` | `Nai2APIProvider` 类实现 |
| `image-gen-tool/image_gen_api.py` | 统一 API 封装 |
| `image-gen-tool/config.json` | Provider 配置（使用占位符，不写入真实密钥） |
| `image-gen-tool/nai_styles.json` | 社区风格库（29 个社区画风预设） |
| `ai_media.py` | CLI 统一入口（nai2api 子命令） |

### 5.3 Provider 架构

```
BaseImageProvider (抽象基类)
└── Nai2APIProvider       → NovelAI 画风预设（Job 异步模式）
```

### 5.4 配置文件：`config.json`

```json
{
  "api_providers": {
    "nai2api": {
      "name": "Nai2API (nai.sta1n.cn)",
      "base_url": "https://nai.sta1n.cn",
      "api_key": "${NAI2API_KEY}",
      "default_params": {
        "style": "2.5d",
        "size": "竖图",
        "steps": 28,
        "scale": 6,
        "cfg": 0,
        "sampler": "k_dpmpp_2m_sde",
        "negative": "",
        "poll_interval": 2,
        "max_poll_time": 180
      }
    }
  },
  "default_provider": "nai2api",
  "output_directory": "./outputs",
  "output_format": "png",
  "save_metadata": true,
  "log_level": "INFO"
}
```

### 5.5 完整实现代码（从零部署必备）

> 7.12 新增大节。**仅靠 §5.7–§5.10 的画师串与 CLI 参数无法独立部署**——必须把以下 Provider 与 CLI 代码完整拼进项目。常见 bug「不管传 `--style` 什么画风，输出全是 2.5d」就是因为自写 Provider 时漏掉了 §5.5.7 的风格路由链路 `_get_artist_string`，只用 `self.default_style` 兜底了。本节按文件给出全部所需代码，按顺序贴齐即可获得全部能力：风格切换、`--variants N` 并发、`--auto-style` 自动检测、社区风格 `community:ID`、内置 7 预设 + 29 社区风格、余额查询。

#### 5.5.1 文件清单（按顺序拼齐）

| 文件 | 内容 | 必需 |
|---|---|---|
| `image-gen-tool/api_handler.py` | 数据类、`BaseAPIProvider`、`Nai2APIProvider` | ✅ |
| `image-gen-tool/nai_styles.json` | 29 个社区风格预置库 | ✅ |
| `image-gen-tool/config.json` | API base_url + 密钥占位/环境变量引用 | ✅ |
| `ai_media.py` | CLI 统一入口（含 `cmd_nai2api` + `nai2api` 子命令） | ✅ |
| `.env` | 实际密钥 `NAI2API_KEY=<STA1N-...>` | ✅（不入库） |

> `image-gen-tool/image_gen_api.py` 是可选的高层封装（依赖 `api_handler` 的 `get_api_provider`），CLI 不直接复用，可省略。

#### 5.5.2 api_handler.py — 模块头与数据类

文件开头放好导入与两个数据类。`ImageGenerationRequest.extra_params` 是画风路由的入口，`--style` 通过它传进 Provider。

```python
"""
API Handler Module - 处理各种图像生成API的调用
支持 Agnes AI、Nai2API、StepFun、自定义 API 等
"""
import os
import requests
import base64
import json
import time
from pathlib import Path
from typing import Optional, Dict, Any, List
from dataclasses import dataclass
from abc import ABC, abstractmethod
import logging

logger = logging.getLogger(__name__)


@dataclass
class ImageGenerationRequest:
    """图像生成请求"""
    prompt: str
    negative_prompt: str = ""
    width: int = 1024
    height: int = 1024
    num_images: int = 1
    seed: Optional[int] = None
    steps: int = 30
    cfg_scale: float = 7.5
    style_preset: Optional[str] = None
    extra_params: Optional[Dict[str, Any]] = None  # ← --style / --size / --scale 等从这里传入


@dataclass
class ImageGenerationResponse:
    """图像生成响应"""
    success: bool
    images: List[bytes]
    image_urls: List[str]
    metadata: Dict[str, Any]
    error_message: Optional[str] = None
    generation_time: Optional[float] = None
```

#### 5.5.3 api_handler.py — BaseAPIProvider 抽象基类

`Nai2APIProvider` 继承此类。三个抽象方法 `generate_text_to_image` / `generate_image_to_image` / `edit_image` 必须实现（Nai2API 后两个返回不支持）。

```python
class BaseAPIProvider(ABC):
    """API 提供商基类"""

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.base_url = config.get('base_url', '')
        self.api_key = config.get('api_key', '')
        self.headers = config.get('headers', {})

    def _get_headers(self) -> Dict[str, str]:
        headers = self.headers.copy()
        for key, value in headers.items():
            if isinstance(value, str):
                headers[key] = value.replace('{api_key}', self.api_key)
        return headers

    def _download_image(self, url: str) -> Optional[bytes]:
        try:
            response = requests.get(url, timeout=60)
            response.raise_for_status()
            return response.content
        except Exception as e:
            logger.error(f"Failed to download image from {url}: {e}")
            return None

    @abstractmethod
    def generate_text_to_image(self, request: ImageGenerationRequest) -> ImageGenerationResponse:
        pass

    @abstractmethod
    def generate_image_to_image(self, request: ImageGenerationRequest,
                                init_image: bytes,
                                strength: float = 0.75) -> ImageGenerationResponse:
        pass

    @abstractmethod
    def edit_image(self, request: ImageGenerationRequest,
                   init_image: bytes,
                   mask_image: Optional[bytes] = None) -> ImageGenerationResponse:
        pass
```

#### 5.5.4 api_handler.py — Nai2APIProvider 类头与常量

类头包含 API 端点说明、`BASE_URL`、社区风格库文件位置、`AUTO_STYLE_KEYWORDS` 自动风格检测关键词表。关键词表按「具体词优先于宽泛词」从上到下排序，匹配第一个命中的行即返回。

```python
class Nai2APIProvider(BaseAPIProvider):
    """Nai2API (nai.sta1n.cn) 图片生成提供商

    Token 格式: STA1N-...
    API 端点:
    - POST /api/jobs          → 创建生成任务
    - GET  /api/jobs/:id      → 轮询任务状态
    - GET  /api/me?token=...  → 查询余额
    """

    BASE_URL = 'https://nai.sta1n.cn'

    # 社区风格库文件位置（与本文件同目录的 nai_styles.json）
    COMMUNITY_STYLES_FILE = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), 'nai_styles.json'
    )

    # 自动风格检测关键词映射（优先级从高到低，具体词优先）
    # 格式: [(关键词列表, 社区风格ID 或 None(表示用内置预设), 风格名称), ...]
    AUTO_STYLE_KEYWORDS = [
        # 高度具体的社区风格
        (['猫娘', 'catgirl', 'nekomimi', '猫耳'], 11, '柔情猫娘3.0'),
        (['米哈游', 'miHoYo', '原神', 'genshin', '崩铁', '星铁', 'honkai', '米家'], 2, '米家画风'),
        (['水彩', '水墨', 'watercolor', 'ink wash'], 4, '水彩水墨风'),
        (['彩铅', 'colored pencil', 'pencil drawing'], 7, '彩铅风'),
        (['童话', 'fairy tale', 'fantasy'], 28, '玻璃糖纸萝莉童话风'),
        (['复古', 'retro', 'vintage', '怀旧'], 12, '复古水彩质感二次元插画'),
        (['华丽', 'gorgeous', '华丽风', 'torino'], 5, 'torino华丽风'),
        (['胶片', 'film', 'film grain', 'cinematic'], 24, '苍银胶片2.5D'),
        (['3D', '3d render', '写实3d'], 21, '3D偏写实唯美风'),
        (['厚涂', 'impasto', 'thick paint'], 26, '二次元厚涂风'),
        (['流光', '光影', 'light effect', 'glow'], 27, '流光溢彩风'),
        (['绚烂', 'vivid', '高绚', 'colorful'], 25, '高绚精绘厚涂风'),
        (['简约', 'simple', 'flat', '纯色', 'minimal'], 29, '2d简约风纯色明亮'),
        (['成熟', 'mature', '氛围', 'atmosphere'], 3, '成熟氛围风'),
        (['韩漫', 'korean', 'manhwa', 'webtoon'], 6, '类似韩漫小清新风'),
        (['萝莉', 'loli'], 28, '玻璃糖纸萝莉童话风'),
        # 较宽泛的关键词放后面
        (['2.5D', '2.5d', '半写实', 'semi-realistic'], 30, '2.5D韩漫厚涂风'),
        (['可爱', 'cute', '活力', 'energetic', 'kawaii'], 1, '挺不错的可爱又有活力的画风'),
        # 内置预设：越具体越靠前，避免被宽泛词截胡
        (['galgame', 'gal', '视觉小说', 'visual novel', '立绘'], None, 'galgame'),
        (['漫画同人', 'comic doujin', 'comicDoujin', 'Comic同人', '动漫同人'], None, 'comicDoujin'),
        (['萝莉唯美', 'loli 2.5d', 'lolita25d', '萝莉2.5D'], None, 'realistic_loli'),
        (['写实萝莉', 'realistic loli', '写实质感', 'photo realistic', 'photo(medium)'], None, 'realistic_loli'),
        (['本子', 'doujin', '同人'], None, 'doujin'),
        (['动漫', 'anime', '旧版', 'classic anime'], None, 'animeOld'),
    ]
```

#### 5.5.5 api_handler.py — ARTIST_PRESETS 与 SIZE_OPTIONS

内置 7 个画风预设的画师串与 9 个尺寸选项。**画师串的完整值见 §5.10.3 内置风格串**（含新旧对比）。以下为代码结构骨架，实际 `value` 字段需要从 §5.10.3 复制当前版本的画师串粘贴进去。

```python
    # 画风预设（从 nai.sta1n.cn/app.js 提取）
    # value 字段的画师串请从 §5.10.3 对应风格块的「当前生效版本」复制
    ARTIST_PRESETS = {
        '2.5d': {
            'label': '2.5D唯美风',
            'value': r'''20::best quality, absurdres, very aesthetic, detailed, masterpiece::, ...（见 §5.10.3 ``2.5d(7.12版)``）''',
        },
        'fresh': {
            'label': '韩漫小清新风',
            'value': r'''masterpiece, best quality,[[[artist:dishwasher1910]]], ...（见 §5.10.3 ``fresh(7.12版)``）''',
        },
        'doujin': {
            'label': '本子里番风',  # 7.12 显示名更新（原「本子动漫风」），值不变
            'value': r'''1.4::asanagi::, ...（见 §5.10.3 ``doujin(7.12版)``）''',
        },
        'galgame': {
            'label': 'GalGame风',
            'value': r'''artist:ningen_mame,, noyu_(noyu23386566),, ...（见 §5.10.3 ``galgame``）''',
        },
        'comicDoujin': {  # 7.12 新增
            'label': '漫画同人风',
            'value': r'''masterpiece,best quality,ultra detailed,by 小田武士,by 内尾和正,by あずーる,TV anime screencap,clean cel shading,soft lineart,subtle bloom glow''',
        },
        'animeOld': {
            'label': '动漫风（旧）',
            'value': r'''artist collaboration, 0.70::artist:necomi ::, ...（见 §5.10.3 ``animeOld``）''',
        },
        'realistic_loli': {  # 7.12 由隐藏改为正式内置，值同步网站 lolita25d
            'label': '2.5D唯美风（萝）',
            'value': r'''20::best quality, absurdres, ...（见 §5.10.3 ``realistic_loli(7.12版)``）''',
        },
    }

    # 尺寸选项与点数消耗
    SIZE_OPTIONS = {
        '竖图':    {'cost': 1,  'label': '竖图(1点)'},
        '横图':    {'cost': 1,  'label': '横图(1点)'},
        '方图':    {'cost': 1,  'label': '方图(1点)'},
        '2K竖图':  {'cost': 15, 'label': '2K竖图(15点)'},
        '2K横图':  {'cost': 15, 'label': '2K横图(15点)'},
        '2K方图':  {'cost': 15, 'label': '2K方图(15点)'},
        '4K竖图':  {'cost': 25, 'label': '4K竖图(25点)'},
        '4K横图':  {'cost': 25, 'label': '4K横图(25点)'},
        '4K方图':  {'cost': 25, 'label': '4K方图(25点)'},
    }
```

#### 5.5.6 api_handler.py — 初始化与社区风格库加载

`__init__` 从 `config.json` 读取默认参数（`default_style` 兜底为 `'2.5d'`）。**这是"全是 2.5d"的兜底来源，合理但不应该是唯一路径**——只要 `_get_artist_string` 路由正常工作，`--style` 传入的值会覆盖 default。`_load_community_styles` 从 `nai_styles.json` 加载 29 个社区风格。

```python
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.default_params = config.get('default_params', {})
        self.api_key = self.api_key or os.environ.get('NAI2API_TOKEN', '')
        self.base_url = (self.base_url or config.get('base_url', self.BASE_URL)).rstrip('/')
        self.default_style = self.default_params.get('style', '2.5d')  # ← 兜底 default
        self.default_size = self.default_params.get('size', '竖图')
        self.default_steps = self.default_params.get('steps', 28)
        self.default_scale = self.default_params.get('scale', 6)
        self.default_cfg = self.default_params.get('cfg', 0)
        self.default_sampler = self.default_params.get('sampler', 'k_dpmpp_2m_sde')
        self.default_negative = self.default_params.get('negative', '')
        self.poll_interval = self.default_params.get('poll_interval', 2)
        self.max_poll_time = self.default_params.get('max_poll_time', 180)
        # 加载社区风格库
        self.community_styles = self._load_community_styles()

    def _load_community_styles(self) -> List[Dict[str, Any]]:
        """从 nai_styles.json 加载社区风格库（29 个预设）"""
        if os.path.exists(self.COMMUNITY_STYLES_FILE):
            try:
                with open(self.COMMUNITY_STYLES_FILE, 'r', encoding='utf-8') as f:
                    styles = json.load(f)
                logger.info(f"Nai2API: 已加载 {len(styles)} 个社区风格")
                return styles
            except Exception as e:
                logger.warning(f"Nai2API: 加载社区风格库失败: {e}")
        return []
```

#### 5.5.7 api_handler.py — 风格列表 / 自动检测 / 社区风格查询

`list_styles` 合并输出内置 7 + 社区 29，是 `--list-styles` 的数据源。`auto_detect_style` 遍历 `AUTO_STYLE_KEYWORDS` 表，按优先级返回第一个命中的 key。`_get_community_style` 解析 `community:ID` 字符串。

```python
    def list_styles(self, include_nsfw: bool = False) -> List[Dict[str, Any]]:
        """列出所有可用风格（内置 + 社区）"""
        result = []
        for key, preset in self.ARTIST_PRESETS.items():
            result.append({
                'id': None,
                'key': key,
                'name': preset['label'],
                'source': 'builtin',
                'nsfw': 'safe',
                'params': {'steps': 28, 'sampler': 'k_dpmpp_2m_sde', 'cfg': 0, 'scale': 6},
            })
        for s in self.community_styles:
            if not include_nsfw and s.get('nsfw') in ('questionable', 'explicit'):
                continue
            result.append({
                'id': s['id'],
                'key': f"community:{s['id']}",
                'name': s['name'],
                'source': 'community',
                'provider': s.get('provider', ''),
                'nsfw': s.get('nsfw', 'safe'),
                'tags': s.get('tags', {}),
                'params': s.get('params', {}),
            })
        return result

    def auto_detect_style(self, prompt: str, include_nsfw: bool = False) -> Optional[str]:
        """根据提示词自动检测最佳风格，返回风格 key"""
        prompt_lower = prompt.lower()
        for keywords, community_id, style_name in self.AUTO_STYLE_KEYWORDS:
            for kw in keywords:
                if kw.lower() in prompt_lower:
                    if community_id is None:
                        return style_name  # 内置预设 key
                    # 检查 NSFW 过滤
                    if not include_nsfw:
                        for s in self.community_styles:
                            if s['id'] == community_id:
                                if s.get('nsfw') in ('questionable', 'explicit'):
                                    break
                                return f"community:{community_id}"
                        break  # NSFW 被过滤，继续匹配下一个
                    return f"community:{community_id}"
        return None  # 未匹配到，使用默认

    def _get_community_style(self, style: str) -> Optional[Dict[str, Any]]:
        """根据 community:ID 格式获取社区风格数据"""
        if not style.startswith('community:'):
            return None
        try:
            cid = int(style.split(':')[1])
        except (ValueError, IndexError):
            return None
        for s in self.community_styles:
            if s['id'] == cid:
                return s
        return None
```

#### 5.5.8 api_handler.py — 风格路由 `_get_artist_string`（关键：防止"全是 2.5d"）

**这是导致"不管 `--style` 传什么都输出 2.5d"问题的最关键方法**。链路优先级：① 社区风格 `community:ID` → ② 内置预设 `ARTIST_PRESETS[style]['value']` → ③ 兜底（原样返回当作自定义画师串）。**自写 Provider 时遗漏此方法、或在 generate 里忘记调用它，就会让所有请求都用到 `self.default_style` 兜底的 2.5d 画师串。**

```python
    def _get_artist_string(self, style: str) -> str:
        """根据画风预设获取画师串（核心路由方法）

        链路:
          1. community:ID → 取 nai_styles.json 中的 artist_prompt
          2. 内置预设     → 取 ARTIST_PRESETS[style]['value']
          3. 都不在       → 原样返回（视为自定义画师串）
        """
        # ① 社区风格优先
        community = self._get_community_style(style)
        if community and community.get('artist_prompt'):
            return community['artist_prompt']
        # ② 内置预设
        preset = self.ARTIST_PRESETS.get(style)
        if preset:
            return preset['value']
        # ③ 兜底：当作自定义画师串返回
        return style
```

> 解释一下 `--artist` 是怎么覆盖整个预设的：`ai_media.py` 在 5.9 节有 `if args.artist: extra['style'] = args.artist`，这会让 `style` 变成自定义画师串，进 `_get_artist_string` 后既不在 community、也不在 ARTIST_PRESETS，命中第 ③ 兜底分支，从而把用户传的 `--artist "by xxx"` 原样作为 artist 参数发出，达到覆盖效果。

#### 5.5.9 api_handler.py — 尺寸解析 _resolve_size

把 `--size` 字符串或宽高比推断成 API 接受的尺寸字符串。

```python
    def _resolve_size(self, request: ImageGenerationRequest, extra: Optional[Dict] = None) -> str:
        """根据请求参数决定尺寸字符串"""
        if extra and extra.get('size'):
            return extra['size']
        w, h = request.width, request.height
        ratio = w / h if h > 0 else 1.0
        if ratio > 1.1:
            return '横图'
        elif ratio < 0.9:
            return '竖图'
        return '方图'
```

#### 5.5.10 api_handler.py — Job 异步流程（创建 / 轮询 / 下载）

Nai2API 不是同步返回，需要 `POST /api/jobs` 提交 → `GET /api/jobs/:id` 轮询 → 下载 `imageUrl`。Provider 已内置完整逻辑，默认轮询间隔 2 秒，超时 180 秒。

```python
    def _create_job(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """POST /api/jobs 创建生成任务"""
        url = f"{self.base_url}/api/jobs"
        headers = {'Content-Type': 'application/json'}
        logger.info(f"Nai2API: Creating job at {url}")
        response = requests.post(url, json=params, headers=headers, timeout=30)
        response.raise_for_status()
        return response.json()

    def _poll_job(self, job_id: str) -> Dict[str, Any]:
        """GET /api/jobs/:id 轮询任务状态"""
        url = f"{self.base_url}/api/jobs/{job_id}"
        params = {'token': self.api_key}
        response = requests.get(url, params=params, timeout=15)
        response.raise_for_status()
        return response.json()

    def _download_result_image(self, image_url: str) -> Optional[bytes]:
        """下载生成的图片"""
        try:
            if image_url.startswith('http'):
                full_url = image_url
            else:
                full_url = f"{self.base_url}{image_url}"
            response = requests.get(full_url, timeout=60)
            response.raise_for_status()
            return response.content
        except Exception as e:
            logger.error(f"Nai2API: Failed to download image from {image_url}: {e}")
            return None
```

#### 5.5.11 api_handler.py — 核心文生图 generate_text_to_image

**这是另一个"全是 2.5d"bug 的关键位置**：`generate_text_to_image` 必须从 `extra.get('style', self.default_style)` 拿 style，再调 `_get_artist_string` 翻译成画师串。若你自写 Provider 时把 `artist` 字段直接写成 `self.default_style` 或写死 `ARTIST_PRESETS['2.5d']['value']`，所有 `--style` 切换都会失效。社区风格命中时，还会用其 `params` 覆盖默认采样器 / 步数 / scale / cfg / negative / 正向 prompt 前缀。

```python
    def generate_text_to_image(self, request: ImageGenerationRequest) -> ImageGenerationResponse:
        """文生图（Job 模式）"""
        if not self.api_key:
            return ImageGenerationResponse(
                False, [], [], {'error': 'NAI2API_TOKEN 未配置'},
                error_message='NAI2API_TOKEN 未配置（需要 STA1N-... 格式的密钥）'
            )

        extra = request.extra_params or {}
        # ★ 关键：从 extra 拿 style，兜底 default_style
        style = extra.get('style', self.default_style)
        size = self._resolve_size(request, extra)
        # ★ 关键：调 _get_artist_string 路由出真正的画师串
        artist_string = self._get_artist_string(style)

        # ── 社区风格参数覆盖 ──
        community = self._get_community_style(style)
        use_steps = request.steps if request.steps else self.default_steps
        use_scale = extra.get('scale', self.default_scale)
        use_cfg = extra.get('cfg', self.default_cfg)
        use_sampler = extra.get('sampler', self.default_sampler)
        use_negative = request.negative_prompt or self.default_negative
        use_prompt = request.prompt

        if community:
            cp = community.get('params', {})
            # 社区风格的默认参数（仅当用户未显式指定时才覆盖）
            if not request.steps:
                use_steps = cp.get('steps', use_steps)
            if 'scale' not in extra:
                use_scale = cp.get('scale', use_scale)
            if 'cfg' not in extra:
                use_cfg = cp.get('cfg', use_cfg)
            if 'sampler' not in extra:
                use_sampler = cp.get('sampler', use_sampler)
            # 社区风格的负面提示词（仅当用户未指定时使用）
            if not request.negative_prompt and community.get('negative_prompt'):
                use_negative = community['negative_prompt']
            # 社区风格的正向提示词前缀
            if community.get('positive_prompt'):
                use_prompt = f"{community['positive_prompt']}, {request.prompt}"
            logger.info(f"Nai2API: 使用社区风格 [{community['id']}] {community['name']}")

        # 构建请求参数
        job_params = {
            'token': self.api_key,
            'tag': use_prompt,                # 提示词
            'model': 'nai-diffusion-4-5-full',
            'artist': artist_string,          # ★ 路由后的画师串
            'size': size,
            'cost': self.SIZE_OPTIONS.get(size, {}).get('cost', 1),
            'steps': use_steps,
            'scale': use_scale,
            'cfg': use_cfg,
            'sampler': use_sampler,
            'negative': use_negative,
            'nocache': '1',
            'noise_schedule': 'karras',
        }

        start_time = time.time()
        try:
            # 1. 创建任务
            job = self._create_job(job_params)
            job_id = job.get('id')
            if not job_id:
                return ImageGenerationResponse(
                    False, [], [], job,
                    error_message=job.get('error', '创建任务失败，未返回 job id')
                )

            # 2. 轮询等待完成
            elapsed = 0
            while elapsed < self.max_poll_time:
                time.sleep(self.poll_interval)
                elapsed += self.poll_interval
                try:
                    status = self._poll_job(job_id)
                except Exception as poll_err:
                    logger.warning(f"Nai2API: Poll error (retryable): {poll_err}")
                    continue

                current_status = status.get('status', '')
                if current_status == 'done':
                    image_url = status.get('imageUrl', '')
                    if not image_url:
                        return ImageGenerationResponse(
                            False, [], [], status,
                            error_message='任务完成但未返回 imageUrl'
                        )
                    image_data = self._download_result_image(image_url)
                    if not image_data:
                        return ImageGenerationResponse(
                            False, [], [], status,
                            error_message='下载生成图片失败'
                        )
                    gen_time = time.time() - start_time
                    return ImageGenerationResponse(
                        success=True,
                        images=[image_data],
                        image_urls=[image_url],
                        metadata={
                            'job_id': job_id,
                            'style': style,
                            'style_name': community['name'] if community
                                          else self.ARTIST_PRESETS.get(style, {}).get('label', style),
                            'size': size,
                            'request_params': job_params,
                            'generation_time': gen_time,
                        },
                        generation_time=gen_time,
                    )
                elif current_status == 'failed':
                    return ImageGenerationResponse(
                        False, [], [], status,
                        error_message=status.get('error', '任务失败')
                    )
                # queued / running → 继续等待

            # 超时
            return ImageGenerationResponse(
                False, [], [], {'job_id': job_id},
                error_message=f"Nai2API: 轮询超时（{self.max_poll_time}秒）"
            )

        except requests.exceptions.Timeout:
            return ImageGenerationResponse(
                False, [], [], {'error': 'Nai2API: 请求超时'},
                error_message='Nai2API: 请求超时'
            )
        except requests.exceptions.RequestException as e:
            error_msg = f"Nai2API: 请求失败 - {e}"
            try:
                err_body = e.response.json()
                error_msg = err_body.get('error', error_msg)
            except Exception:
                pass
            return ImageGenerationResponse(False, [], [], {'error': error_msg}, error_message=error_msg)
        except Exception as e:
            error_msg = f"Nai2API: 未知错误 - {e}"
            return ImageGenerationResponse(False, [], [], {'error': str(e)}, error_message=str(e))
```

#### 5.5.12 api_handler.py — img2img / edit 桩 + 余额查询

Nai2API 当前仅支持文生图，图生图与图像编辑返回不支持。`check_balance` 调 `GET /api/me?token=...`，配合 `--balance` 使用。

```python
    def generate_image_to_image(self, request: ImageGenerationRequest,
                                init_image: bytes,
                                strength: float = 0.75) -> ImageGenerationResponse:
        return ImageGenerationResponse(
            False, [], [], {'error': 'Nai2API 暂不支持 img2img'},
            error_message='Nai2API 当前仅支持文生图'
        )

    def edit_image(self, request: ImageGenerationRequest,
                   init_image: bytes,
                   mask_image: Optional[bytes] = None) -> ImageGenerationResponse:
        return ImageGenerationResponse(
            False, [], [], {'error': 'Nai2API 暂不支持图像编辑'},
            error_message='Nai2API 当前仅支持文生图'
        )

    def check_balance(self) -> Dict[str, Any]:
        """查询余额"""
        if not self.api_key:
            return {'error': 'NAI2API_TOKEN 未配置'}
        url = f"{self.base_url}/api/me"
        params = {'token': self.api_key}
        try:
            response = requests.get(url, params=params, timeout=10)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            return {'error': str(e)}
```

#### 5.5.13 nai_styles.json — 社区风格库参考

29 个社区风格预置在 `image-gen-tool/nai_styles.json` 中，由 `_load_community_styles` 启动时加载。每个条目字段如下：

| 字段 | 说明 |
|---|---|
| `id` | 社区风格 ID，CLI 用 `--style community:<id>` 引用 |
| `name` | 风格名称（中文） |
| `provider` / `image_provider` | 风格贡献者 |
| `artist_prompt` | 该风格的画师串（直接作为 `artist` 参数发出） |
| `positive_prompt` | 正向提示词前缀（拼在用户 prompt 前面） |
| `negative_prompt` | 风格默认负面提示词（用户未指定时生效） |
| `nsfw` | 标记：`safe` / `questionable` / `explicit`（不传 `--nsfw` 时会被过滤） |
| `params` | 风格默认参数 `{steps, sampler, cfg, scale}` |
| `tags` | 分类标签（如 `{"画风": [...]}`） |

示例（完整 29 条请直接使用 `image-gen-tool/nai_styles.json`）：

```json
{
  "id": 30,
  "name": "2.5D韩漫厚涂风",
  "provider": "小鱼",
  "image_provider": "小鱼",
  "artist_prompt": "saigalisk, 0.5::cutesexyrobutts, aleriia v, krekkov::, ...",
  "positive_prompt": "",
  "negative_prompt": "{{{{bad anatomy}}}},{bad feet},bad hands,...",
  "nsfw": "safe",
  "params": {"steps": 28, "sampler": "k_dpmpp_2m_sde", "cfg": 0, "scale": 6},
  "tags": {"画风": ["2.5D，厚涂，韩系插画"]}
}
```

> NSFW 分布：`safe` 20 个 / `questionable` 2 个 / `explicit` 7 个。不带 `--nsfw` 时只暴露 20 个 safe 条目，带 `--nsfw` 时全部 29 个可用。

#### 5.5.14 ai_media.py — 模块级常量与支撑函数

`ai_media.py` 是 CLI 统一入口，在文件头放好路径常量与若干支撑函数。`ensure_paths` 把 `image-gen-tool` 加入 `sys.path` 让 `from api_handler import ...` 可以工作。`bootstrap` 是每个命令函数开头都调用的入口。

```python
#!/usr/bin/env python3
"""Unified AI media helper for /workspace."""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

WORKSPACE = Path("/workspace")
IMAGE_TOOL = WORKSPACE / "image-gen-tool"
VIDEO_TOOL = WORKSPACE / "video-gen-tool"
VIDEO_PACKAGE_LINK = WORKSPACE / "video_gen_tool"
DEFAULT_IMAGE_OUTPUT = IMAGE_TOOL / "outputs"
DEFAULT_VIDEO_OUTPUT = VIDEO_TOOL / "output"

DEFAULT_FREE_IMAGE_PROVIDER = "custom"   # Agnes AI free tier
DEFAULT_FREE_VIDEO_PROVIDER = "agnes"


def ensure_paths() -> None:
    """把 image-gen-tool / workspace 加入 sys.path 让 import 稳定"""
    if str(IMAGE_TOOL) not in sys.path:
        sys.path.insert(0, str(IMAGE_TOOL))
    if str(WORKSPACE) not in sys.path:
        sys.path.insert(0, str(WORKSPACE))
    if not VIDEO_PACKAGE_LINK.exists():
        try:
            VIDEO_PACKAGE_LINK.symlink_to(VIDEO_TOOL, target_is_directory=True)
        except FileExistsError:
            pass


def load_dotenv(path: Path) -> None:
    """加载 .env 到 os.environ，不覆盖已有变量"""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def bootstrap() -> None:
    """每个 cmd_xxx 函数开头都调这个"""
    ensure_paths()
    load_dotenv(VIDEO_TOOL / ".env")
    DEFAULT_IMAGE_OUTPUT.mkdir(parents=True, exist_ok=True)
    DEFAULT_VIDEO_OUTPUT.mkdir(parents=True, exist_ok=True)
    current_video_output = os.environ.get("VIDEO_GEN_OUTPUT_DIR", "")
    if not current_video_output or not Path(current_video_output).is_absolute():
        os.environ["VIDEO_GEN_OUTPUT_DIR"] = str(DEFAULT_VIDEO_OUTPUT)


def next_dated_output_path(output_dir: Path, ext: str) -> Path:
    """返回 YYYYMMDD_NNN.ext 命名规则下一条可用路径"""
    output_dir.mkdir(parents=True, exist_ok=True)
    date = datetime.now().strftime("%Y%m%d")
    ext = ext.lstrip(".")
    max_no = 0
    for path in output_dir.glob(f"{date}_*.{ext}"):
        stem = path.stem
        suffix = stem[len(date) + 1:]
        if suffix.isdigit():
            max_no = max(max_no, int(suffix))
    return output_dir / f"{date}_{max_no + 1:03d}.{ext}"
```

#### 5.5.15 ai_media.py — cmd_nai2api 完整处理函数

`cmd_nai2api` 是 `nai2api` 子命令的入口。流程：从 `config.json` 加载 Provider → 处理 `--balance` / `--list-styles` → 处理 `--auto-style` → 构建 `extra_params`（**`style` 在这里进 extra**）→ 处理 `--variants N`（ThreadPoolExecutor 并发）→ 保存图片 → 输出 JSON。**注意 `if args.artist: extra['style'] = args.artist` 这行**——这是 `--artist` 覆盖整个画风预设的实现。

```python
def cmd_nai2api(args: argparse.Namespace) -> int:
    """Nai2API (nai.sta1n.cn) 文生图命令（含社区风格库）"""
    bootstrap()
    from api_handler import Nai2APIProvider, ImageGenerationRequest

    # 从 config.json 加载配置
    config_path = str(IMAGE_TOOL / "config.json")
    provider_config = {}
    if os.path.exists(config_path):
        with open(config_path) as f:
            cfg = json.load(f)
        provider_config = cfg.get("api_providers", {}).get("nai2api", {})

    provider = Nai2APIProvider(provider_config)

    # 仅查询余额
    if getattr(args, 'balance', False):
        balance = provider.check_balance()
        print(json.dumps(balance, ensure_ascii=False, indent=2))
        return 0 if 'error' not in balance else 1

    # 列出所有风格
    if getattr(args, 'list_styles', False):
        include_nsfw = getattr(args, 'nsfw', False)
        styles = provider.list_styles(include_nsfw=include_nsfw)
        print(f"\n{'='*80}")
        print(f"  Nai2API 可用风格列表（共 {len(styles)} 个）")
        print(f"{'='*80}\n")
        print(f"  {'来源':<10} {'Key':<22} {'名称':<30} {'NSFW':<12} {'参数'}")
        print(f"  {'-'*90}")
        for s in styles:
            source = '内置' if s['source'] == 'builtin' else '社区'
            p = s.get('params', {})
            param_str = f"step={p.get('steps','-')} {p.get('sampler','-')} cfg={p.get('cfg','-')} scale={p.get('scale','-')}"
            print(f"  {source:<10} {s['key']:<22} {s['name']:<30} {s['nsfw']:<12} {param_str}")
        print(f"\n  使用方式: --style <Key>  或  --auto-style（自动匹配）")
        return 0

    # ── 自动风格检测 ──
    include_nsfw = getattr(args, 'nsfw', False)
    style = args.style
    auto_detected = None

    if getattr(args, 'auto_style', False):
        auto_detected = provider.auto_detect_style(args.prompt, include_nsfw=include_nsfw)
        if auto_detected:
            style = auto_detected
            community = provider._get_community_style(style)
            if community:
                style_name = community['name']
            else:
                style_name = provider.ARTIST_PRESETS.get(style, {}).get('label', style)
            print(f"[auto-style] 检测到关键词 → 自动选择风格: {style_name} ({style})")
        else:
            print(f"[auto-style] 未匹配到特定风格，使用默认: {style}")

    # 构建 extra_params
    extra = {
        'style': style,  # ★ style 进入 extra，之后 Provider 用 _get_artist_string 路由
        'size': args.size,
        'scale': args.scale,
        'cfg': args.cfg,
        'sampler': args.sampler,
    }

    # 自定义画师串覆盖整个画风预设
    if args.artist:
        extra['style'] = args.artist  # ★ --artist 覆盖整个预设

    # 尺寸映射（用于宽高比推断，实际尺寸由 provider 内部用 size 字符串决定）
    size_wh = {
        '竖图': (832, 1216), '横图': (1216, 832), '方图': (1024, 1024),
        '2K竖图': (832, 1216), '2K横图': (1216, 832), '2K方图': (1024, 1024),
        '4K竖图': (832, 1216), '4K横图': (1216, 832), '4K方图': (1024, 1024),
    }
    w, h = size_wh.get(args.size, (832, 1216))

    request = ImageGenerationRequest(
        prompt=args.prompt,
        negative_prompt=args.negative or "",
        width=w,
        height=h,
        steps=args.steps,
        cfg_scale=args.scale,
        extra_params=extra,
    )

    # ── 并发多张变体生成 ──
    n_variants = max(1, min(6, getattr(args, 'variants', 1)))
    if n_variants == 1:
        result = provider.generate_text_to_image(request)
        results = [result]
    else:
        import concurrent.futures as cf
        print(f"[variants] 并发生成 {n_variants} 张变体中，请稍候", file=sys.stderr)
        with cf.ThreadPoolExecutor(max_workers=n_variants) as pool:
            futures = [pool.submit(provider.generate_text_to_image, request) for _ in range(n_variants)]
            done, _ = cf.wait(futures, timeout=900)
            results = [f.result() for f in done if not f.exception()]
        success_count = sum(1 for r in results if r and r.success)
        print(f"[variants] 完成 {success_count}/{n_variants} 张成功", file=sys.stderr)

    # 保存图片
    saved = []
    if any(r.success and r.images for r in results):
        from PIL import Image
        import io
        for r in results:
            if r.success and r.images:
                for image_bytes in r.images:
                    out = next_dated_output_path(DEFAULT_IMAGE_OUTPUT, "png")
                    img = Image.open(io.BytesIO(image_bytes))
                    img.save(out)
                    saved.append(str(out))

    # 收集 image_urls（转为完整 URL，用于直接在 Markdown 中显示）
    image_urls = []
    for r in results:
        if r.success and r.image_urls:
            for u in r.image_urls:
                if u.startswith('http'):
                    image_urls.append(u)
                else:
                    image_urls.append(f"https://nai.sta1n.cn{u}")

    best = max(results, key=lambda r: (r.generation_time if r and r.success else 0), default=results[0])
    output = {
        'success': any(r.success for r in results),
        'variants_requested': n_variants,
        'variants_success': sum(1 for r in results if r and r.success),
        'saved_files': saved,
        'image_urls': image_urls,
        'provider': 'nai2api',
        'style': style,
        'style_name': best.metadata.get('style_name', style) if best and best.metadata else style,
        'auto_detected': auto_detected is not None,
        'size': args.size,
        'generation_time': best.generation_time if best else 0,
        'errors': [r.error_message for r in results if r and r.error_message] or None,
    }
    print(json.dumps({k: v for k, v in output.items() if v is not None}, ensure_ascii=False, indent=2))
    return 0 if output['success'] else 1
```

#### 5.5.16 ai_media.py — nai2api 子命令参数定义

argparse 中 `nai2api` 子命令的完整参数定义。`--style` 默认 `2.5d`（兜底），可用值包括 7 个内置 key 和 `community:<ID>` 形式的社区风格。`--variants` 默认 1，范围 1–6。

```python
    # ── Nai2API (nai.sta1n.cn) ──
    nai2 = sub.add_parser("nai2api",
                          help="Nai2API 文生图（内置7预设 + 29社区风格库）")
    nai2.add_argument("prompt", nargs="?", default="",
                      help="英文提示词，逗号分割（--list-styles 时可不填）")
    nai2.add_argument("--list-styles", action="store_true",
                      help="列出所有可用风格（内置+社区）")
    nai2.add_argument("--auto-style", action="store_true",
                      help="根据提示词自动匹配风格")
    nai2.add_argument("--nsfw", action="store_true",
                      help="显示/使用 NSFW 风格")
    nai2.add_argument("--style", default="2.5d",
                      help="画风: 内置(2.5d/fresh/doujin/galgame/comicDoujin/"
                           "realistic_loli/animeOld) 或 社区(community:ID), "
                           "用 --list-styles 查看")
    nai2.add_argument("--size", default="竖图",
                      choices=["竖图", "横图", "方图",
                               "2K竖图", "2K横图", "2K方图",
                               "4K竖图", "4K横图", "4K方图"],
                      help="尺寸 (标准=1点, 2K=15点, 4K=25点)")
    nai2.add_argument("--steps", type=int, default=28,
                      help="采样步数 (1-28)")
    nai2.add_argument("--scale", type=float, default=6.0,
                      help="Scale (1-20), 推荐 6")
    nai2.add_argument("--cfg", type=float, default=0.0,
                      help="CFG/Rescale (0-1), 推荐 0")
    nai2.add_argument("--sampler", default="k_dpmpp_2m_sde",
                      choices=["k_dpmpp_2m_sde", "k_dpmpp_2m", "k_dpmpp_sde",
                               "k_dpmpp_2s_ancestral", "k_euler_ancestral", "k_euler"],
                      help="采样器")
    nai2.add_argument("--negative", default="",
                      help="负面提示词")
    nai2.add_argument("--artist", default="",
                      help="自定义画师串（覆盖画风预设）")
    nai2.add_argument("--variants", type=int, default=1,
                      help="并发生成张数（1-6），多张同时跑选最佳")
    nai2.add_argument("--balance", action="store_true",
                      help="仅查询余额，不生成图片")
    nai2.set_defaults(func=cmd_nai2api)
```

> `main()` 入口：`parser = build_parser(); args = parser.parse_args(); return args.func(args)`。

#### 5.5.17 "全是 2.5d" 问题诊断清单

部署完发现"不管传什么 `--style` 出来都是 2.5d 画风"，按以下顺序排查：

| 可能原因 | 排查方法 | 修复 |
|---|---|---|
| **A. 没实现 `_get_artist_string`，直接用 `self.default_style`** | 在 `generate_text_to_image` 里搜 `artist_string =` 是否调用了 `_get_artist_string` | 补上 §5.5.8 的 `_get_artist_string` 并在 generate 里调用 |
| **B. 没从 `extra.get('style')` 拿 style** | 在 generate 里搜 `style = ` 看是不是 `extra.get('style', self.default_style)` | 改成 `style = extra.get('style', self.default_style)`，让 `--style` 能覆盖 default |
| **C. artist 字段写死成 2.5d 画师串** | 在 `job_params['artist'] =` 这行检查旁边是不是常量 | 改成 `artist_string = self._get_artist_string(style)`，然后 `'artist': artist_string` |
| **D. `ARTIST_PRESETS` dict 里没有对应 key** | 跑 `python3 ai_media.py nai2api --list-styles`，看目标 key 在不在 | 把 §5.10.3 中对应风格的当前版本画师串加入 `ARTIST_PRESETS` |
| **E. CLI 的 `extra['style']` 没传** | 在 `cmd_nai2api` 里搜 `'style': ` 看 extra 里有没有 | 补上 `'style': style,` 到 `extra` dict |
| **F. config.json 默认风格 2.5d，但 CLI 没传 --style** | 不传 `--style` 时确实会用 2.5d，这是设计如此 | 主动传 `--style galgame` 或修改 config.json 默认值 |

> 验证修复：跑 `python3 ai_media.py nai2api "1girl, solo, silver hair" --style galgame --size 竖图`，检查输出 JSON 中 `style` 字段是否为 `galgame`，`style_name` 是否为 `GalGame风`。如果是，说明路由正常。

### 5.6 画风预设

#### 内置风格（7 种）

> 7.12 更新：内置风格从 5 个增加到 7 个，新增 `comicDoujin` 和 `realistic_loli`（由隐藏改为正式内置）。部分画师串已同步 nai.sta1n.cn 7/11 更新，旧的画师串保留在下方标注 `(旧)`。

| 风格 ID | 名称 | 说明 | 7.12 更新 |
|---|---|---|---|
| `2.5d` | 2.5D 唯美风 | 半写实半二次元，多画师加权叠权 | 画师串全面更新，新增 pale aesthetic / silver-toned |
| `fresh` | 韩漫小清新风 | 清新明亮 | 加了 masterpiece/best quality 前缀 + soft lighting |
| `doujin` | 本子里番风 | 同人志画风 | 显示名从「本子动漫风」改为「本子里番风」，值不变 |
| `galgame` | GalGame 风 | 美少女游戏风 | 无变化 |
| `comicDoujin` | 漫画同人风 | **7.12 新增**，clean cel shading 动画截图质感 | 新增预设 |
| `animeOld` | 动漫风（旧） | 经典动画风 | 无变化 |
| `realistic_loli` | 2.5D 唯美风（萝） | 写实质感萝莉体型 | 值同步网站 lolita25d，加了 lifelike flesh + pale aesthetic |

#### 社区风格（29 种）

通过 `--style community:ID` 使用。查看完整列表：

```bash
python3 ai_media.py nai2api --list-styles
```

社区风格的 artistPrompt / positivePrompt / negativePrompt / params 会自动覆盖默认值。  
用户显式指定的参数始终优先于社区风格默认值。

#### 自动风格匹配

```bash
python3 ai_media.py nai2api "prompt" --auto-style
```

根据提示词中的关键词自动匹配最合适的画风。  
7.12 更新：auto-detect 关键词表已新增 `漫画同人 / 动漫同人 → comicDoujin` 和 `萝莉唯美 / lolita25d → realistic_loli`，并按「具体词优先于宽泛词」重排了顺序，避免 `动漫同人` 被宽泛的 `动漫` 截胡。

### 5.7 内置画风质量提示词

当前工具链在 `Nai2APIProvider.ARTIST_PRESETS` 中内置了画风相关的质量提示词，也就是画师串 / 风格串。调用 `--style` 时会自动拼接对应风格串，不需要每次手写。  
如果使用 `--artist`，则会覆盖画风预设中的画师串。

#### 5.7.1 通用质量词

推荐在普通提示词中保留以下通用质量词，适用于大多数画风：

```text
masterpiece, best quality, absurdres, very aesthetic, detailed, ultra detailed, highly finished, no text
```

#### 5.7.2 通用负面提示词

推荐搭建后直接使用以下负面提示词，能显著减少低质量、文字、水印和肢体错误：

```text
lowres, worst quality, low quality, normal quality, blurry, jpeg artifacts, signature, watermark, username, text, cropped, out of frame, bad anatomy, bad hands, bad feet, bad proportions, extra fingers, missing fingers, fused fingers, too many fingers, poorly drawn hands, poorly drawn face, malformed limbs, extra arms, extra legs, extra limbs, missing arms, missing legs, mutated hands, mutation, deformed, disfigured, cloned face, long neck, ugly
```

#### 5.7.3 内置风格串

以下内容来自当前工作区 `image-gen-tool/api_handler.py` 中的内置配置。复制到新环境后，搭建完成即可直接使用这些风格。  
7.12 更新内容已标注 `(7.12版)`，旧画师串保留并标注 `(旧)` 供对比参考。

---

##### `2.5d`：2.5D唯美风

**2.5d(7.12版)** — 当前生效版本，画师串全面更新，多画师加权叠权

```text
20::best quality, absurdres, very aesthetic, detailed, masterpiece::, 20::highly finished::, 10::ultra detailed::, 5::masterpiece::, 5::best quality::, 2.4::kidmo::, 1.2::omone hokoma agm::, 1.1::dino, wanke, liduke::, 0.8::rurudo, mignon, artist:pottsness, artist:toosaka asagi::, 0.7::misaka_12003-gou::, 0.6::artist:chocoan, artist:ciloranko, artist:rhasta, artist:sho_sho_lwlw::, dino_(dinoartforame), agoto, akakura, year 2025, textless version, no text, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has graphic texture, realistic skin surface, and lifelike flesh with little obliques::, smooth line, glossy skin, realistic, 4k, 1.63::photorealistic::, 1.63::photo(medium)::, 3::simple background::, 2::depth of field::, 1.5::vivid color, lively color::, desaturated, muted tones, cinematic desaturation, pale aesthetic, silver-toned, -2::green::, -1.5::vibrant, colorful, saturated::
```

**2.5d(旧)** — 7.5日版本，单画师 misaka_12003-gou 撑全场

```text
0.9::misaka_12003-gou ::, dino_(dinoartforame), wanke, liduke, year 2025, realistic, 4k, -2::green ::, textless version, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has lively color, graphic texture, realistic skin surface, and lifelike flesh with little obliques::. 1.63::photorealistic::, 1.63::photo(medium)::, \n20::best quality, absurdres, very aesthetic, detailed, masterpiece::,, very aesthetic, masterpiece, no text,
```

> **更新要点**：旧版为单一画师 `misaka_12003-gou` 为主；新版改为 kidmo / rurudo / mignon / pottsness / toosaka asagi 多画师加权叠权，品质词前置（20::best quality:: 等），新增 `pale aesthetic` 和 `silver-toned` 冷调风格。色调从偏暖偏亮变为冷调银灰。

---

##### `fresh`：韩漫小清新风

**fresh(7.12版)** — 当前生效版本，加了品质词前缀和 soft lighting

```text
masterpiece, best quality,[[[artist:dishwasher1910]]], {{yd_(orange_maru)}}, [artist:ciloranko], [artist:sho_(sho_lwlw)], [ningen mame], soft lighting,year 2024
```

**fresh(旧)** — 7.5日版本，无品质词前缀

```text
[[[artist:dishwasher1910]]], {{yd_(orange_maru)}}, [artist:ciloranko], [artist:sho_(sho_lwlw)], [ningen mame], year 2024,
```

> **更新要点**：新版在画师串前加了 `masterpiece, best quality,` 前缀，末尾加了 `soft lighting`。画面更干净柔和。

---

##### `doujin`：本子里番风

**doujin(7.12版)** — 当前生效版本，显示名更新为「本子里番风」，画师串值不变

```text
1.4::asanagi::,{{{{{artist:asanagi}}}}},1.2::xiaoluo_xl::,1.3::Artist: misaka_12003-gou::,1.2::Artist:shexyo::,0.7::Artist:b.sa_(bbbs)::,1::Artist:qiandaiyiyu::,1.05::artist:natedecock::,1.05::artist:kunaboto::,0.75::artist:kandata_nijou::,1.05::artist:zer0.zer0 ::,1.05::artist:jasony::,0.75::misaka_12003-gou ::, dino_(dinoartforame), wanke, liduke, year 2025, realistic, 4k, -2::green ::, {textless version, The image is highly intricate finished drawn,write realistically,true to life}, 1.35::A highly finished photo-style artwork that has lively color, graphic texture, realistic skin surface, and lifelike flesh with little obliques::, 1.63::photorealistic::,3::age slider::,1.63::photo(medium)::, 2::best quality, absurdres, very aesthetic, detailed, masterpiece::,-4::Muscle definition, abs::
```

**doujin(旧)** — 7.5日版本，显示名为「本子动漫风」，值相同

> 同上

> **更新要点**：画师串值不变，仅显示名从「本子动漫风」改为「本子里番风」，命名更准确。

---

##### `galgame`：GalGame风

**galgame(7.12版)** — 当前生效版本，无变化

```text
artist:ningen_mame,, noyu_(noyu23386566),, toosaka asagi,, location,\n20::best quality, absurdres, very aesthetic, detailed, masterpiece::,:,, very aesthetic, masterpiece, no text,
```

> 7.12 无更新，与旧版完全一致。

---

##### `comicDoujin`：漫画同人风 — 7.12 新增

**comicDoujin(7.12版)** — 新增预设，clean cel shading 动画截图质感

```text
masterpiece,best quality,ultra detailed,by 小田武士,by 内尾和正,by あずーる,TV anime screencap,clean cel shading,soft lineart,subtle bloom glow
```

> **说明**：7.12 新增内置预设。画师为小田武士 / 内尾和正 / あずーる，配合 TV anime screencap、clean cel shading、soft lineart、subtle bloom glow。适合高动态动画截图感场景（山坡逆风 / 天台回眸等），日常配图不推荐做默认。

---

##### `animeOld`：动漫风（旧）

**animeOld(7.12版)** — 当前生效版本，无变化

```text
artist collaboration, 0.70::artist:necomi ::, 0.80::artist:tan (tangent) ::, 1.38::artist:kanda done ::, 1.22::artist:quasarcake ::, 1.22::artist:atdan ::, 0.94::artist:fuumi (radial engine) ::, 1.70::artist:john kafka ::, 0.60::artist:meisansan ::, 0.98::artist:ogipote ::, 0.44::artist:nixeu ::, 0.74::artist:mignon ::, 0.94::artist:rangu ::, 1.18::artist:hiten (hitenkei) ::, 1.24::artist:freng ::, 0.56::artist:miwabe sakura ::, year 2024, perspective
```

> 7.12 无更新，与旧版完全一致。

---

##### `realistic_loli`：2.5D唯美风（萝）

**realistic_loli(7.12版)** — 当前生效版本，值同步网站 lolita25d

```text
20::best quality, absurdres, very aesthetic, detailed, masterpiece::, 20::highly finished::, 10::ultra detailed::, 5::masterpiece::, 5::best quality::, 2.4::kidmo::, 1.2::omone hokoma agm::, 1.1::dino, wanke, liduke::, 0.8::rurudo, mignon, artist:pottsness, artist:toosaka asagi::, 0.7::misaka_12003-gou::, 0.6::artist:chocoan, artist:ciloranko, artist:rhasta, artist:sho_sho_lwlw::, dino_(dinoartforame), agoto, akakura, 0.9::rurudo(Only body shape), mignon(Only body shape) :: year 2025, textless version, {{petite,loli}}, Petite figure, no text, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has graphic texture, realistic skin surface, and lifelike flesh with little obliques::, smooth line, glossy skin, realistic, 4k, 1.63::photorealistic::, 1.63::photo(medium)::, 3::simple background::, 2::depth of field::, 1.5::vivid color, lively color::, desaturated, muted tones, cinematic desaturation, pale aesthetic, silver-toned, -2::green::, -1.5::vibrant, colorful, saturated::
```

**realistic_loli(旧)** — 7.5日版本，写实质感萝莉

```text
20::best quality, absurdres, very aesthetic, detailed, masterpiece::, 20::highly finished::, 10::ultra detailed::, 5::masterpiece::, 5::best quality::, 2.4::kidmo::, 1.2::omone hokoma agm::, 1.1::dino, wanke, liduke::, 0.8::rurudo, mignon, artist:pottsness, artist:toosaka asagi::, 0.7::misaka_12003-gou::, 0.6::artist:chocoan, artist:ciloranko, artist:rhasta, artist:sho_sho_lwlw::, dino_(dinoartforame), agoto, akakura, 0.9::rurudo(Only body shape), mignon(Only body shape) :: year 2025, textless version, {{petite,loli}}, Petite figure, no text, The image is highly intricate finished drawn. Only the character's face is in anime style, but their body is in realistic style. 1.35::A highly finished photo-style artwork that has graphic texture, realistic skin surface, and lifelike skin with little obliques::, smooth line, glossy skin, realistic, 4k, 1.63::photorealistic::, 1.63::photo(medium)::, 3::simple background::, 2::depth of field::, 1.5::vivid color, lively color::, desaturated, muted tones, cinematic desaturation, silver-toned, -2::green::, -1.5::vibrant, colorful, saturated::
```

> **更新要点**：`lifelike skin` 改为 `lifelike flesh`（写实肉感更重），新增 `pale aesthetic`（整体偏冷白调）。显示名从「写实质感萝莉」改为「2.5D唯美风（萝）」。

#### 5.7.4 并发生成 `--variants N`（7.12 新增）

支持用 `--variants N`（N = 1-6）并发生成多张变体。使用 ThreadPoolExecutor 并发提交 N 个独立 job，同一提示词不同随机种子出 N 张不同的变体。耗时与单张几乎无差。

适用场景：手脸容易崩的复杂姿态，一次出多张挑最佳。

```bash
python3 ai_media.py nai2api "1girl, silver hair, complex pose" \
    --style galgame --size 竖图 --variants 3
```

> 实测 3 张并发 3/3 成功，耗时约 38 秒。

#### 5.7.5 通用角色提示词模板

如果要搭建完成后马上测试人物图，可以使用以下不含私人角色设定的通用模板：

```text
1girl, solo, long hair, bright eyes, cute face, school uniform, standing, looking at viewer, masterpiece, best quality, absurdres, very aesthetic, detailed
```

推荐配套负面提示词：

```text
lowres, worst quality, low quality, normal quality, blurry, jpeg artifacts, signature, watermark, username, text, bad anatomy, bad hands, bad feet, bad proportions, extra fingers, missing fingers, fused fingers, malformed limbs, extra limbs, missing limbs, long neck, deformed, disfigured, ugly
```

示例命令：

```bash
python3 ai_media.py nai2api \
  "1girl, solo, long hair, bright eyes, cute face, school uniform, standing, looking at viewer, masterpiece, best quality, absurdres, very aesthetic, detailed" \
  --style galgame \
  --size 竖图 \
  --negative "lowres, worst quality, low quality, normal quality, blurry, jpeg artifacts, signature, watermark, username, text, bad anatomy, bad hands, bad feet, bad proportions, extra fingers, missing fingers, fused fingers, malformed limbs, extra limbs, missing limbs, long neck, deformed, disfigured, ugly"
```

### 5.8 尺寸与点数

| 尺寸 | 分辨率级别 | 消耗点数 |
|---|---|---|
| 竖图 / 横图 / 方图 | 标准 | 1 点 |
| 2K竖图 / 2K横图 / 2K方图 | 2K | 15 点 |
| 4K竖图 / 4K横图 / 4K方图 | 4K | 25 点 |

### 5.9 CLI 使用

```bash
# 基本生图
python3 ai_media.py nai2api "1girl, silver hair, blue eyes, masterpiece" \
    --style galgame --size 竖图

# 带负面提示词
python3 ai_media.py nai2api "1girl, silver hair" \
    --style galgame --negative "lowres, bad anatomy, bad hands"

# 使用社区风格
python3 ai_media.py nai2api "1girl, flower field" \
    --style community:4 --size 竖图

# 自定义采样器和步数
python3 ai_media.py nai2api "prompt" \
    --sampler k_euler_ancestral --steps 20 --scale 8

# 并发生成多张变体（7.12 新增）
python3 ai_media.py nai2api "1girl, complex pose" \
    --style galgame --size 竖图 --variants 3

# 列出所有可用风格
python3 ai_media.py nai2api --list-styles

# 含 NSFW 风格
python3 ai_media.py nai2api --list-styles --nsfw

# 自动匹配风格
python3 ai_media.py nai2api "watercolor landscape" --auto-style

# 查询余额
python3 ai_media.py nai2api --balance
```

### 5.10 完整参数列表

```
positional arguments:
  prompt                英文提示词，逗号分割

options:
  --list-styles         列出所有可用风格（内置+社区）
  --auto-style          根据提示词自动匹配风格
  --nsfw                显示/使用 NSFW 风格
  --style STYLE         画风: 内置(2.5d/fresh/doujin/galgame/comicDoujin/realistic_loli/animeOld)
                        或 社区(community:ID)
  --size SIZE           竖图/横图/方图/2K竖图/2K横图/2K方图/4K竖图/4K横图/4K方图
  --steps STEPS         采样步数 (1-28)
  --scale SCALE         Scale (1-20), 推荐 6
  --cfg CFG             CFG/Rescale (0-1), 推荐 0
  --sampler SAMPLER     k_dpmpp_2m_sde / k_dpmpp_2m / k_dpmpp_sde /
                        k_dpmpp_2s_ancestral / k_euler_ancestral / k_euler
  --negative NEGATIVE   负面提示词
  --artist ARTIST       自定义画师串（覆盖画风预设）
  --variants N          并发生成张数（1-6），多张同时跑选最佳（7.12 新增）
  --balance             仅查询余额
```

### 5.11 部署后持久化图片引用规则

工具链搭建完成并验证出图成功后，需要将以下图片引用规则持久化存储到长期记忆系统中，确保后续对话中 AI 助手始终遵守：

**需持久化的规则内容：**

```text
1. 图片引用必须使用标准 Markdown 语法 ![](url)，URL 放在圆括号中，不是方括号
2. URL 必须来自 ai_media.py 返回 JSON 的 image_urls 字段，禁止手写假链接
3. 没有实际执行生图命令时，回复中不出现任何 ![](url) 图片引用
4. 若使用 image2 / GPT-Image-2 等只返回本地文件路径的模型，不要在聊天中用本地路径作 Markdown 图片引用，需先上传到可访问图床再引用
5. 生图失败时简短说明原因，不以假链接代替
```

**操作方式：**

- 若部署环境中存在长期记忆系统（如 memory_tool、knowledge base 等），将上述规则作为一条记忆记录存入，标题如「图片引用格式规则」，内容为上述 5 条规则。
- 若部署环境中**没有**长期记忆系统，应提醒用户：当前环境无持久化记忆机制，图片引用规则需要在每次对话开始时通过 system prompt 或技能文件重新加载，否则 AI 助手可能遗忘引用格式。

**验证方法：**

存储后，在新对话中要求 AI 助手生成一张图片并引用，检查其输出是否符合上述 5 条规则。若违反任一条，说明记忆未被正确加载，需重新存储或检查加载流程。

### 5.12 文件命名与输出

- 文件名格式：`YYYYMMDD_NNN.png`（如 `20260622_001.png`）
- 输出目录：`/workspace/image-gen-tool/outputs/nai/`
- 按角色分类：`角色/`、`场景/`、`其他/`

### 5.13 在文本消息中引用图片

生图完成后，解析命令返回 JSON 中的 `image_urls` 字段，取其中完整 URL，用标准 Markdown 图片语法直接内联引用。

正确格式：

```markdown
![](https://example.com/path/to/generated_image.png)
```

也可以在方括号里写替代文本：

```markdown
![生成图片](https://example.com/path/to/generated_image.png)
```

错误格式，不要使用：

```markdown
![https://example.com/path/to/generated_image.png]
```

格式要求：

- 必须使用 `![](图片URL)` 或 `![描述](图片URL)`。
- URL 必须放在圆括号里，不是方括号里。
- URL 必须来自 `image_urls` 字段，不能手写假链接。
- 如果没有真实 URL，就不要输出图片引用。
- 回复末尾只展示图片时，推荐单独一行放置图片语法。

示例：

```markdown
![](https://nai.sta1n.cn/api/images/img_xxxxx/content)
```

---

## 6. 环境变量配置

### 6.1 `/workspace/.env`

```env
# Nai2API
NAI2API_KEY=<填入本地环境变量值，不要写入公开文档>
```

### 6.2 密钥配置说明

| 服务 | 配置项 | 计费方式 |
|---|---|---|
| Nai2API | `NAI2API_KEY` | 按点消耗 |

> **安全提醒**：`.env` 文件包含密钥，切勿提交到 Git。建议在 `.gitignore` 中添加 `.env`。

---

## 7. 快速验证

搭建完成后，按以下步骤验证：

### 7.1 基础环境

```bash
source /workspace/.venv/bin/activate
python3 -c "import requests, PIL; print('✓ 依赖正常')"
```

### 7.2 查询余额

```bash
python3 /workspace/ai_media.py nai2api --balance
# 期望：返回当前余额点数
```

### 7.3 测试生图

```bash
python3 /workspace/ai_media.py nai2api \
    "1girl, solo, long hair, bright eyes, masterpiece, best quality, absurdres, very aesthetic, detailed" \
    --style galgame --size 竖图 \
    --negative "lowres, worst quality, low quality, bad anatomy, bad hands, text, watermark"
# 期望：约 30~40 秒后生成 PNG，并在返回 JSON 的 image_urls 字段中给出可访问图片 URL
```

### 7.4 测试并发生成（7.12 新增）

```bash
python3 /workspace/ai_media.py nai2api \
    "1girl, solo, long hair, complex pose, masterpiece, best quality, absurdres, very aesthetic, detailed" \
    --style galgame --size 竖图 --variants 3
# 期望：约 35~40 秒后生成 3 张 PNG，返回 JSON 中含 3 个 image_urls
```

---

## 8. 附录

### 8.1 7.12 更新摘要

| 更新项 | 内容 |
|--------|------|
| `2.5d` 画师串 | 全面更新为多画师加权叠权（kidmo/rurudo/mignon/pottsness 等）+ pale aesthetic + silver-toned |
| `fresh` 画师串 | 加了 masterpiece/best quality 前缀 + soft lighting |
| `doujin` 显示名 | 从「本子动漫风」改为「本子里番风」，值不变 |
| `realistic_loli` 画师串 | lifelike skin → lifelife flesh，新增 pale aesthetic，显示名改为「2.5D唯美风（萝）」 |
| `comicDoujin` 新增 | 漫画同人风预设：小田武士/内尾和正/あずーる + TV anime screencap + clean cel shading |
| 内置风格数量 | 5 个 → 7 个（新增 comicDoujin，realistic_loli 由隐藏改为正式内置） |
| `--variants N` | 新增并发生成参数，支持 1-6 张并发 |
| auto-detect 关键词表 | 新增 comicDoujin / realistic_loli 触发词，具体词优先于宽泛词重排 |

### 8.2 关键踩坑记录

#### Nai2API 异步轮询

**问题**：Nai2API 不是同步返回，需要轮询等待。  
**解决**：Provider 已实现 Job 提交 → 轮询 → 下载的完整流程，默认超时 180 秒，轮询间隔 2 秒。

#### 社区风格参数覆盖

**问题**：社区风格的默认参数可能覆盖用户设置。  
**解决**：用户显式指定的参数始终优先于社区风格默认值。

#### 风格 ID 格式

**问题**：社区风格需要用 `community:ID` 格式，不是直接写 ID 数字。  
**解决**：用 `--list-styles` 查看完整列表，确认 ID 编号。

#### Markdown 图片引用格式

**问题**：旧文档曾写成 `![url]`，这是错误写法，只会显示替代文本或无法正确渲染图片。  
**解决**：使用标准 Markdown 图片语法 `![](url)` 或 `![描述](url)`，并且 URL 必须来自生图返回的 `image_urls` 字段。

#### 画风质量词可直接复用

**问题**：新环境搭建完成后，如果只写简单提示词，出图质量和画风稳定性不够。  
**解决**：本文档已内置当前工作区使用的 7 个内置画风串（含新旧对比）、通用质量词、通用负面词和通用人物模板。搭建完成后可直接复制使用。

### 8.3 购买提示与隐私检查

环境搭建完成后，如果还没有可用的 Nai2API Key，可以前往以下地址购买或获取：

🔗 https://www.qianxun1688.com/links/D07F549B

发布或分享本文档前，请确认：

- 不包含真实 API Key、Token、Cookie 或账号信息。
- 不包含个人角色设定、私有提示词模板或专属角色描述。
- 不包含只适用于个人工作区的路径、余额截图或私人备注。
- 示例命令中的图片 URL 使用占位符或公开可访问的示例地址。
- 购买链接可以保留，用于提示新环境搭建者获取可用 Key。

---

*最后更新：2026年7月12日*  
*备注：本文档不含任何真实密钥，不包含私人角色提示词；保留购买入口提示，已同步 7.12 Nai2API 内置画风质量提示词更新（7 个内置风格新旧对比 + --variants 并发生成功能 + auto-detect 关键词表更新）；7.12 新增 §5.5 完整实现代码（数据类、BaseAPIProvider、Nai2APIProvider 全套方法、ai_media.py cmd_nai2api 与 argparse 定义、社区风格 nai_styles.json 结构参考、"全是 2.5d" 问题诊断清单），从零部署拼齐即可发挥全部能力。*