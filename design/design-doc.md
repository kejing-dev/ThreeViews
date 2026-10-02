# Spatial App Design Doc — 三视图 (ThreeViews)

## 1 · Frame

**Subject** — 大学《工程制图 / 画法几何》里的三视图：按国标（GB/T 14692）第一角画法，把一个机械零件正投影成主视图、俯视图、左视图。零件在观察者和投影面之间：主视图落在零件后面的正立面 V，俯视图落在下面的水平面 H，左视图落在右侧的侧立面 W；三个面沿 OX、OZ 轴摊平成一张图纸，满足"长对正、高平齐、宽相等"。线型按 GB/T 4457.4：可见轮廓粗实线，不可见轮廓细虚线，轴线和对称中心线细点画线。

**Viewer & context** — 学过或正在学工程制图的学生、工程师。坐在或站在桌边，一次 5–15 分钟。头显在 Shared Space 里，旁边可能还开着别的窗口。阅读距离：2D 窗口约 1.5 m，投影箱在手边 0.5–1 m，可以绕着看。

**The single job** — 给一个 3D 机械零件，让人亲眼看到它怎样变成主、俯、左三个视图，并能拿着图纸对着零件把每一条线读明白。

**Spatial justification**

| Core task | Affordance | Why spatial beats flat | 2D counterfactual |
| --------- | ---------- | ---------------------- | ----------------- |
| 理解三个投影面的位置关系和投影方向 | Depth, Position | 三个互相垂直的面和三个投影方向是真的 3D 关系。零件悬在三个面之间，人可以探头从投影方向看过去，看到的就是那个视图。 | 课本上是一张轴测图，角度是定死的；CAD 里是三个并排的 2D 视口，看不到面与面的关系。 |
| 把零件上的一条棱对到视图上的一条线 | Simulation, Position | 投影线从 3D 棱上的点出发落到面上，起点和落点同时在眼前，换个角度还能看到被挡住的棱为什么是虚线。 | 平面图上只能靠编号或颜色对应，被遮挡的结构只能在脑子里想。 |
| 理解图纸为什么是"主视图在左上、俯视图在下、左视图在右" | Motion | H 面向下翻、W 面向右翻，三个面摊成一张纸，这个动作本身就是答案。 | 课本用两张图加箭头表示翻转，动画视频能演示，但视角仍是别人选的。 |
| 选零件、读图、核对三等关系 | 无（保持 2D） | 这部分就是一张图纸和一个列表，平面最清楚。 | 就用平面做：放在 Planar 窗口里，不做空间化。 |

形态由此确定：3D 关系放进一个 Volumetric 投影箱，它是应用的主窗口，开在用户正前方；选零件和读图是 2D 的事，各放进一个 Planar 窗口，分列投影箱左右。三者都在 Shared Space。

**Assumptions**

| Assumption | Confidence | What changes if wrong |
| ---------- | ---------- | --------------------- |
| 用户要的是国标第一角画法（主、俯、左），不是第三角画法 | 高（用户说的是国内大学的工程制图课） | 投影面位置和视图排布要整体换成第三角 |
| v1 是自由观察，不做判分练习 | 中 | 需要加题库和判题界面（方向 C），零件库和算法可以原样复用 |
| 零件库 8 个由程序定义的组合体就够开始用 | 中 | 需要加导入模型的入口；三视图由算法从模型算出，导入后不需要手画 |
| v1 不标注尺寸 | 中 | 图纸上要加尺寸线和尺寸数字，属于增量功能 |
| 界面中英双语同时显示（中文在上或在前，英文跟随） | 高（用户在第二轮明确要求） | 只影响文字和控件宽度 |

## 2 · Explore — alternatives

**Direction generation** — subagent-per-direction（三个独立上下文并行，各自只拿到共同的 Frame 和一条发散约束，互相看不到对方的结果）。

### Direction A — 投影箱 + 图纸窗口（两个表面）

一个约 35 cm 的 Volumetric 投影箱：三个半透明投影面 V、H、W，零件悬在中间；旁边一个 Planar 窗口，左边是零件库，右边是按国标排布的图纸。在窗口里选零件，箱里换零件，三个面上画出粗实线、虚线、点画线。拉"展开"，H 面向下翻、W 面向右翻，摊平后的线正好就是窗口里那张图。Signature：展开这个动作把箱子的三个面交给图纸。Main risk：两个表面分散注意力，Shared Space 里系统决定它们的摆放，离得远时 3D 和 2D 的对应会变弱。

### Direction B — 单个 Planar 窗口，图纸就是摊平的投影箱

只有一个窗口，本身是一张国标图纸，零件悬在图纸右下角空白象限前方（最多 500 dp 深度）。一个"折叠"滑杆把图纸上 V、H、W 三块区域折成围着零件的三面角，再放平。Signature：图纸和投影箱是同一个物体。Main risk：Planar 窗口可用深度只有 640 dp，折起来的面会挡住正要读的视图，细虚线在折起、带深度偏移的面上难读；也不能绕着零件看。

### Direction C — 练习优先

以课本习题组织："补画第三视图""看图选零件""找出画错的视图"。2D 窗口是习题纸，旁边一个体积里是空的三面角，提交答案后投影线扫出真实视图并与答案叠在一起。Signature：对错由投影本身判定，不是一个对勾。Main risk：题库是重内容，每个零件都需要干扰项和故意画错的视图，才谈得上教学价值。

**Decision matrix**

| Direction | Task efficiency | Spatial value | PICO comfort | Domain fit | Risk | Distinctiveness |
| --------- | --------------- | ------------- | ------------ | ---------- | ---- | --------------- |
| A | 高：选零件一步到位，读图和看箱互不遮挡 | 高：可绕看、可探头沿投影方向看 | 好：箱子桌面大小，窗口标准尺寸 | 高：就是课本的三投影面体系 | 中：两个表面的摆放 | 高：展开动作 |
| B | 中：折叠状态下图纸被挡 | 中：深度受限，不能绕看 | 中：细线带深度偏移 | 高 | 高：640 dp 深度和遮挡 | 高 |
| C | 中：先做题才能看 | 高 | 好 | 高 | 高：题库内容量 | 中 |

**Chosen: A** — 它直接满足用户的三条硬要求（Shared Space、在 2D 窗口里选零件、三个视图分别投影出来），3D 关系放在能绕着看的体积里，读图留在最清楚的平面上。
**Rejected** — B 输在 Planar 窗口 640 dp 的深度上限：折起的投影面会遮住视图，也不能从侧面和上面看进去，而"沿投影方向看过去"正是这个应用的核心。C 输在内容量：没有一批合格的干扰零件和错图，练习就不成立；它的判定方式作为后续功能保留，A 的零件库和投影算法可以原样支撑它。

## 3 · Plan

### Visual tokens

主题从制图桌来：描图纸、墨线、蓝色铅笔的作图线、红色铅笔的点画线。

| `ColorScheme` role  | Exact hex or explicit Vibrant inheritance   | Used for |
| ------------------- | ------------------------------------------- | -------- |
| `fillPrimary`       | `inherit SpatialUI Vibrant fillPrimary`     | 零件库选中行底 |
| `fillSecondary`     | `inherit SpatialUI Vibrant fillSecondary`   | 列表行、分段控件底 |
| `fillTertiary`      | `inherit SpatialUI Vibrant fillTertiary`    | 图标按钮底 |
| `fillLight`         | `inherit SpatialUI Vibrant fillLight`       | 悬停浅底 |
| `labelPrimaryLight` | `#FFFFFF`                                   | 强调色按钮上的文字 |
| `labelPrimary`      | `inherit SpatialUI Vibrant labelPrimary`    | 玻璃上的主文字 |
| `labelSecondary`    | `inherit SpatialUI Vibrant labelSecondary`  | 零件说明、次要文字 |
| `labelTertiary`     | `inherit SpatialUI Vibrant labelTertiary`   | 提示文字 |
| `labelQuaternary`   | `inherit SpatialUI Vibrant labelQuaternary` | 禁用文字 |
| `lightenHover`      | `inherit SpatialUI Vibrant lightenHover`    | 悬停 |
| `lightenPressed`    | `inherit SpatialUI Vibrant lightenPressed`  | 按下 |
| `error`             | `#C62828`                                   | 视图计算失败 |
| `alert`             | `#E08A00`                                   | 投影箱已关闭的提示 |
| `passable`          | `#2E8B57`                                   | 三等关系对齐成功的标记 |
| `interaction`       | `#2B5FD9`                                   | 主按钮、选中、开关打开（蓝图蓝） |
| `dividerLine`       | `inherit SpatialUI Vibrant dividerLine`     | 分隔线 |

**Brand / decorative tokens outside `ColorScheme`**

| Token name | Exact hex | Used for |
| ---------- | --------- | -------- |
| `Paper` | `#F6F3EA` | 图纸底色（窗口内的不透明纸面卡片，不是窗口根） |
| `PaperEdge` | `#D9D3C3` | 图纸边框、标题栏格线 |
| `Ink` | `#16181C` | 粗实线、虚线、图纸上的文字 |
| `InkSoft` | `#5B616B` | 图纸上的次要文字 |
| `CenterLine` | `#C4372B` | 细点画线（轴线、对称中心线） |
| `RayBlue` | `#2B5FD9` | 投影线、对齐线（长对正、高平齐、宽相等） |
| `PlaneV` | `#DCE6F7` | 投影箱里 V 面的淡色 |
| `PlaneH` | `#F4ECD2` | H 面的淡色 |
| `PlaneW` | `#DCEFE2` | W 面的淡色 |
| `PartBody` | `#B9C3CE` | 零件表面 |

**Semantic colors**

| Status | Design color / role | Redundant cue (shape/icon) | UI label |
| ------ | ------------------- | -------------------------- | -------- |
| 视图计算失败 | `error` | 三角警示图标 | "这个零件的视图没有算出来" |
| 投影箱已关闭 | `alert` | 方框图标 | "投影箱已关闭" |
| 当前聚焦的视图 | `interaction` | 视图名称前的实心圆点 + 该面描边 | "主视图 / 俯视图 / 左视图" |

**Type** — Noto Sans SC。图纸标题和零件名用 title（Bold），视图名称用 label（Medium），说明用 body（Regular）。图纸上的字（视图名、标题栏）固定为墨色。字号沿用 `PicoTheme.typography` 的刻度，只换字体。

**Materials / depth** — Planar 窗口根保持系统 `Material.Regular` 玻璃。图纸是窗口内一张不透明纸面卡片（`Paper`），因为工程图的黑线必须有稳定的浅底才读得清。投影箱里三个投影面是半透明淡色薄板，零件不透明，线画在面上（略微抬离面，避免闪烁）。聚焦某个视图时，该面描边加亮，其余两个面降到一半透明度。

**Glass premise** — 根表面用 SpatialUI Web `vibrant` 主题的 `Material.Regular` 玻璃；不定义替代的灰色根、模糊配方或不透明背景。

### Signature element

**展开**：投影箱里 H 面绕 OX 轴向下翻 90°，W 面绕 OZ 轴向右翻 90°，三个面摊成一张纸，线的位置和 2D 窗口里那张图纸一一对应。其余界面保持安静：窗口里只有列表、图纸和一条工具条。

### Surface sizing

| Window / surface | Type (Planar/Volumetric/Stage) | Content | Default (dp / m) | Min | Max |
| ---------------- | ------------------------------ | ------- | ---------------- | --- | --- |
| 投影箱（默认窗口，居中） | Volumetric, Shared Space, 系统管理的动态尺度, 重力对齐, 不显示底板 | 零件 + 三个投影面 + 投影线；窗口级工具条 | 0.8 × 0.72 × 0.9 m | 系统默认下限 | 系统默认上限 |
| 零件库窗口（投影箱左侧，启动时打开） | Planar, Shared Space | 零件库列表 | 360 × 720 dp | 320 × 480 dp | 480 × 960 dp |
| 图纸窗口（投影箱右侧，按需打开） | Planar, Shared Space | 图纸；窗口级工具条（对齐线） | 960 × 720 dp | 720 × 540 dp | 1600 × 1200 dp |

### Layout

三个表面从左到右：零件库、投影箱、图纸。唯一主焦点是投影箱里的零件和三个投影面。投影箱下方是窗口级工具条，十个控件；图纸窗口下方是只有一个开关的工具条。界面文字中英双语：按钮、开关、分段是两行（中文在上，英文小字在下），其余标签中文在前、英文跟随。

```
   零件库 Parts            投影箱（主窗口，居中）                   图纸 Sheet（按需）
  +--------------+        +-----------------------+        +---------------------------+
  | 零件库 Parts |       /|  V 主视图 Front      /|        | 主视图 Front view | 左视图 |
  | 01 L 形块    |      / |     ┌──────┐        / |        |   ┌─────┐  │  ┌────┐      |
  | 02 阶梯块    |     +-----------------------+  | W      |   └─────┘  │  └────┘      |
  | 03 斜切块    |     |  |     │ 零件 │       |  |        | ───────────O────────────   |
  | 04 带孔底板● |     |  +-----└──────┘-------|--+        |   ┌─────┐  │ ╲ 45°        |
  | ...          |     | /   H 俯视图 Top      | /         |   └─────┘  │   标题栏     |
  +--------------+     +-----------------------+           +---------------------------+
        [全部|主视|俯视|左视] [展开] [投影线][虚线] [复位] [零件库][图纸]           [对齐线]
                         （投影箱下方的工具条）                            （图纸下方的工具条）
```

展开后：V 面不动，H 面翻到 V 的正下方，W 面翻到 V 的右边，整组平移，使摊平的图纸在箱内居中。零件不消失：它离开三面角，落到图纸空着的第四象限（H 面右边、W 面下边），成为一个小的轴测参考（转 35°、俯 20°，等比缩到象限的九成以内）。对齐线和 45° 线只画在 2D 图纸上，不画在摊平的箱子上。

### State graph

| State | Primary task | Primary focus | Components | Data deps | Entry/exit |
| ----- | ------------ | ------------- | ---------- | --------- | ---------- |
| 观察（折叠） | 看零件和三个面上的投影 | 投影箱 | 零件、三面、可选投影线 | 当前零件的三视图线集 | 启动默认；从"摊平"点合上 |
| 聚焦视图 | 只看一个投影方向 | 被选中的投影面和它的投影线 | 分段控件、投影面、图纸上的高亮视图 | 同上 + 聚焦的视图 | 分段控件选主视/俯视/左视，或点投影面，或点图纸上的视图；选"全部"、再点一次同一个面或视图、点零件，都退出 |
| 摊平（展开） | 看三面如何变成一张图 | 摊平的图纸和第四象限里的零件 | 展开开关 | 同上 | 点"展开"；再点合上 |
| 视图计算失败 | 知道出了问题并重试或换一个零件 | 图纸上的错误占位 | 错误占位 + "重试" | 计算结果为错误 | 计算出错（图纸窗口没开时自动打开）；重试成功或换零件 |

投影线、虚线、对齐线是三个独立开关；零件库窗口、图纸窗口的开合是两个独立开关。它们都叠加在以上状态之上。聚焦在摊平时照常可用。

| Transition | From → To | Trigger | Explicit confirm? |
| ---------- | --------- | ------- | ----------------- |
| 选零件 | 任意 → 同一状态（换数据） | 点零件库窗口的一行 | 否 |
| 聚焦 | 观察/摊平 → 聚焦视图 | 分段控件、点投影面、点图纸上的视图 | 否 |
| 取消聚焦 | 聚焦视图 → 观察 | 分段控件选"全部"，或再点一次，或点零件 | 否 |
| 展开 | 观察/聚焦 → 摊平 | 点"展开" | 否 |
| 合上 | 摊平 → 观察（带着聚焦合上时回到聚焦视图） | 再点"展开" | 否 |
| 开关投影线/虚线 | 任意 → 同一状态 | 投影箱工具条上的开关；摊平时"投影线"不可用 | 否 |
| 开关对齐线 | 任意 → 同一状态 | 图纸工具条上的开关 | 否 |
| 复位视角 | 观察/聚焦 → 同一状态（转角回到 28°） | 点"复位"；摊平的图纸不转动，所以摊平时不可用 | 否 |
| 开合零件库窗口 | 任意 → 同一状态 | 工具条的"零件库"，或窗口自己的关闭键 | 否 |
| 开合图纸窗口 | 任意 → 同一状态 | 工具条的"图纸"，或窗口自己的关闭键 | 否 |
| 计算失败/重试 | 任意 → 视图计算失败 → 原状态 | 计算出错 / 点"重试" | 否 |

### Components

- **零件库行**（零件库窗口） — 任务：选零件。数据：零件库（编号、中英文名称、中英文结构说明、难度）。状态：默认、悬停、选中。
- **投影箱（3D）** — 任务：观察投影关系。数据：零件网格、三组投影线、投影线段、展开进度、聚焦视图、绕竖轴的转角。交互：横向拖动转动整箱；点某个面聚焦该视图，再点一次或点零件回到全部。三个面各有一个中英文名牌（V 主视图 Front、H 俯视图 Top、W 左视图 Left）。领域专属 3D 内容。
- **视图聚焦分段控件**（投影箱工具条） — 任务：只看一个投影方向。数据：聚焦的视图（全部/主视/俯视/左视）。
- **展开开关**（投影箱工具条） — 任务：看三面摊平。数据：是否展开。
- **投影线、虚线开关**（投影箱工具条） — 任务：控制箱里显示哪些线。数据：两个布尔值；虚线同时作用于图纸。
- **复位按钮**（投影箱工具条） — 任务：投影箱转回 28° 的教科书视角。
- **零件库、图纸开关**（投影箱工具条） — 任务：打开或关闭两个 2D 窗口，并显示它们是否开着。数据：两个窗口的开合状态。
- **图纸**（图纸窗口） — 任务：读三视图、核对三等关系。数据：当前零件按算法算出的三组线（可见、不可见、中心线）加外接尺寸。变体：显示/隐藏虚线；显示/隐藏对齐线；某个视图高亮；加载中；错误占位加"重试"。纸面上有投影轴、45° 线、中英文视图名称和标题栏（名称、比例、图号、单位、第一角画法符号）。领域专属绘图。
- **对齐线开关**（图纸工具条） — 任务：显示长对正、高平齐、宽相等三组对齐线。数据：一个布尔值。

### SpatialUI Web component map

| Planned element | SpatialUI tag | API checked in                 | Public event/state | Custom fallback reason |
| --------------- | ------------- | ------------------------------ | ------------------ | ---------------------- |
| 主题（16 个 `ColorScheme` 角色） | `PicoTheme.install({ scheme: "vibrant", colorScheme })` | `js/tokens.js` | 11 个继承角色逐个取自 `PicoTheme.vibrantColorScheme()`；5 个定值角色（`labelPrimaryLight` `error` `alert` `passable` `interaction`）用 §3 的 hex | n/a |
| 两个 Planar 窗口的根（`Material.Regular` 玻璃） | 库的 `--Background-MaterialRegular` 变量 + `.surface.material-regular` 的模糊配方 | `js/overlay.js`（`overlayCss`） | `#parts-window`、`#sheet-window`：`background: var(--Background-MaterialRegular, rgba(255,255,255,.34))`，`backdrop-filter: blur(50px) saturate(1.18)`，圆角 32 | 库没有独立的窗口根元素或类；这段玻璃只存在于浮层组件的 shadow 样式里，所以根节点引用同一个变量和同一组数值，没有另写配方 |
| 零件库标题、数量（"零件库 Parts"、"8 个组合体 · 8 solids"） | `sui-text` | `js/text.js` | `variant` `color` 属性；`#part-count` 绑定零件数 | n/a |
| 零件库行（编号、中英名称、中英说明、中英难度） | `sui-side-navigation-item`，选中圆点用 `sui-dot-badge` | `js/composition-items.js`、`js/badge.js` | `select` 事件（`detail.value` = 零件 id），在 `#part-list` 上监听；`selected` 属性；`leading` / 默认 / `trailing` 三个 slot。选择器 `.part-row[value]` | 没有用 `sui-list-item`：它的 `.item` 写死 `min-width: 360px`（`js/list-item.js`），最小宽度 320 dp 的零件库窗口放不下。`sui-side-navigation-item` 没有容器色属性，选中底色通过在该行上设 `--sui-fill-secondary: var(--sui-fill-primary)` 得到 §3 规定的 `fillPrimary` |
| 视图聚焦分段控件（全部 All / 主视 Front / 俯视 Top / 左视 Left） | 4 个 `sui-segment-item`，放在一个布局容器 `#focus-control` 里 | `js/composition-items.js`（对照 `js/segment-control.js`） | 每段的 `select` 事件（`detail.value` = `all｜front｜top｜left`），在容器上监听；`selected`、`disabled` 属性；默认 slot 放两行标签 | 没有用 `sui-segment-control`：它只把 `options` 里的逗号分隔文字渲染成单行标签，没有 slot，放不了"中文在上、英文在下"的两行标签。改用目录里的组合式子组件 `sui-segment-item`；外层容器只做布局，取 `segment-control.js` 的同一组数值（内边距 4、间距 4、圆角 16、`fillTertiary` 底） |
| 展开开关 | `sui-toggle-button` | `js/toggle-button.js` | `checked-change` 事件（`detail.checked`）；`checked`、`disabled`、`checked-container-color` 属性；默认 slot 放两行标签。选择器 `#unfold-toggle` | n/a |
| 投影线、虚线开关（投影箱工具条） | `sui-toggle-button` × 2 | `js/toggle-button.js` | 同上。选择器 `#rays-toggle` `#hidden-toggle` | n/a |
| 复位按钮 | `sui-button` | `js/button.js` | `click-action` 事件；默认 slot 放两行标签。选择器 `#reset-view` | n/a |
| 零件库、图纸两个窗口开关 | `sui-toggle-button` × 2 | `js/toggle-button.js` | `checked-change` 事件 → 打开或关闭对应窗口；`checked` 始终反映窗口是否打开（用窗口自己的系统关闭键关掉时也会变为未选中）。选择器 `#parts-toggle` `#sheet-toggle` | n/a |
| 投影箱的窗口级工具条 | `sui-toolbar`，分组线用 `sui-divider` | `js/window-components.js`、`js/overlay.js`、`js/divider.js` | `for="projection-box"`、`offset-y="100"`；默认 slot。选择器 `#box-toolbar` | n/a |
| 图纸窗口的窗口级工具条 + 对齐线开关 | `sui-toolbar` + `sui-toggle-button` | `js/window-components.js`、`js/toggle-button.js` | `for="sheet-window"`、`offset-y="100"`；`hidden` 随图纸窗口开关；`#align-toggle` 的 `checked-change` 事件。选择器 `#sheet-toolbar` | n/a |
| 视图计算失败占位（图标、中英文字、"重试 Retry"） | `sui-icon` + `sui-text` + `sui-button` | `js/icon.js`、`js/text.js`、`js/button.js` | `#compute-retry` 的 `click-action` 事件；`#sheet-error` 的 `hidden` | 占位本身画在图纸纸面上，是图纸（领域专属表面）的一个变体，外层只是布局容器 |
| 正在计算提示（"正在计算视图… / Computing the views…"） | `sui-text` × 2 | `js/text.js` | `#sheet-loading` 的 `hidden` 随计算状态切换 | 同上，画在图纸纸面上 |
| 两个 Planar 窗口的系统关闭键（系统界面，不是应用界面） | `sui-icon-button` × 2 | `js/icon-button.js` | `click-action` 事件 → 关闭该窗口并把工具条上对应的开关置为未选中。选择器 `#parts-close` `#sheet-close` | n/a |
| 图纸（三视图、轴、45° 线、对齐线、标题栏） | 无，自绘 SVG | — | `.sheet-view` 上的 click 和 Enter/空格键 → 聚焦该视图（折叠、摊平都可以） | 工程图是领域专属绘图，目录里没有对应组件 |
| 投影箱（三个投影面、零件、投影线、展开、摊平后的零件立体图） | 无，自绘 CSS 3D + SVG | — | `#vol-viewport` 上的指针拖动 → 转角；点 `.plane` → 聚焦该视图（折叠、摊平都可以） | 3D 空间内容，目录里没有对应组件 |

## 4 · Critique (before build)

**Generic-default simulation** — 懒办法会做成一个"3D 模型查看器"：深色面板、中间一个可旋转的模型、右边三个小缩略图叫"前/顶/侧"，再配一个青色强调色。它展示的是三个渲染角度，不是正投影，没有投影面，没有线型，也没有摊平。

**Where the plan risked that default, and what I changed.**
- 最初想把三个视图做成窗口里的三个小预览，那就退化成查看器了。改为：视图画在投影面上，由算法算出可见线和不可见线，图纸按国标排布并带标题栏。
- 最初配色想用深色加亮色线（"蓝图"风格），那是通用默认。改为描图纸底加墨线，蓝色只留给作图线，红色只留给点画线，和制图课上的用法一致。
- 工具条最初塞了八个控件。削到六个，尺寸标注和练习模式移出 v1。

**Hard checks**

| Check                                                       | Pass? | Evidence |
| ----------------------------------------------------------- | ----- | -------- |
| ≥3 real alternatives with rejection reasons                 | 是 | §2 的 A、B、C 在表面数量、空间化程度和信息架构上各不相同，B、C 的落选理由是具体事实 |
| Every core task has a 2D counterfactual                     | 是 | §1 表格四行都有 |
| Form/scale follows the spatial justification (not habit)    | 是 | 3D 关系进 Volumetric，读图留在 Planar；没有做成 Stage |
| Every window/surface size was internally validated          | 是 | Planar 1280×800 在 65°×40° 清晰视场内，行高和按钮不小于 56 dp；投影箱 0.7 m 在臂展内 |
| Attachments justified vs None/in-place                      | 是 | 工具条是始终可用的箱体控制，贴在窗口边缘不占图纸面积；与"放在页面内"相比不会随图纸缩放而挤压 |
| Domain-swap test (design would change)                      | 是 | 投影面、线型、摊平、标题栏、三等关系对齐线都是制图专属；换领域后都不成立 |
| Every core component has data source + task                 | 是 | 见 Components |
| All 16 `ColorScheme` roles are explicitly defined           | 是 | 见 Visual tokens |
| Every standard control/system surface maps to SpatialUI Web | Build 阶段填表后确认 | 见 component map |
| Single primary focus / clear decision                       | 是 | 窗口的主焦点是图纸，投影箱的主焦点是零件与三面 |

## 5 · Build — coverage manifest

`preview.html`：单文件，范围 `web_design_validation_only`。完整的 `spatialui.bundle.js` 原样内联在第一个 `<script>` 里，应用脚本在第二个 `<script>` 里；没有外部引用。

第 2 轮评审之后是三个表面，从左到右：`#parts-window`（Planar"零件库 Parts"，启动时打开）、`#projection-box`（Volumetric 投影箱，应用的主窗口，始终在水平正中）、`#sheet-window`（Planar"图纸 Sheet"，启动时关闭）。状态写在 `#room` 上：`data-state`（`observe` / `focused` / `unfolded` / `compute-error`）、`data-focus`、`data-unfolded`、`data-rays`、`data-hidden-lines`、`data-align`、`data-parts`、`data-sheet`、`data-compute`（`loading` / `ok` / `error`）；展开动画的阶段写在 `#projection-box[data-fold]` 上（`folded` → `unfolding` → `unfolded` → `folding` → `closing` → `folded`）。下表里的"｜"表示"或"。

| #   | Kind (state/transition/component/binding) | From design doc | Implemented in preview.html (selector/where) |
| --- | ----------------------------------------- | --------------- | -------------------------------------------- |
| S1 | state | 观察（折叠） | 启动默认。`#room[data-state="observe"]`，`#projection-box[data-fold="folded"]`：三面成直角，零件 `#vol-part` 在中间，面上画出三个投影 |
| S2 | state | 聚焦视图 | `#room[data-state="focused"][data-focus="front｜top｜left"]`。图纸上 `.sheet-view.is-focus`（描边 + 名称前的圆点），其余两个 `.is-dim`；箱里 `.plane.is-focus`（描边），其余两个 `.plane.is-dim`（50% 透明度）；只留该方向的 `.ray-*`。摊平时同样可以聚焦：`data-state` 仍是 `unfolded`，`data-focus` 记着聚焦的视图 |
| S3 | state | 摊平（展开） | `#room[data-state="unfolded"]`。`.plane-h` 绕 OX 向下翻，`.plane-w` 绕 OZ 向右翻；之后 `#vol-group` 向左上平移、视角转正。面一动，投影线 `.ray` 就消失。零件 `#vol-part` 落到摊平图纸空着的第四象限成为小立体图（中心在象限中心，外接球离纸面 2 mm，转 35°、俯 20°，外接球不超过象限的 90%，不放大）。摊平的箱子上只有三个面和零件，没有 45° 线和对齐线 |
| S4 | state | 视图计算失败 | `#room[data-state="compute-error"]`，显示在图纸窗口里：`#sheet-drawing` 隐藏，`#sheet-error` 出现（`error` 色三角图标 + 中英文说明 + `#compute-retry`）。计算失败时如果图纸窗口是关着的，会自动打开。箱里零件还在，三个面是空的；分段控件和对齐线开关变为 disabled |
| W1 | state（独立开关） | 零件库窗口 开 / 关 | `#room[data-parts="open｜closed"]`，`#parts-slot[hidden]`。叠加在 S1–S4 之上，不改变状态名；关掉后投影箱仍在正中 |
| W2 | state（独立开关） | 图纸窗口 开 / 关 | `#room[data-sheet="open｜closed"]`，`#sheet-slot[hidden]`、`#sheet-toolbar[hidden]`。叠加在 S1–S4 之上 |
| T1 | transition | 选零件：任意 → 同一状态 | 点 `.part-row[value]` → `select` 事件 → `selectPart()`。先是 `data-compute="loading"`（图纸上显示 `#sheet-loading`，投影箱保留上一个零件），约 0.3 秒后换成新零件；聚焦和展开状态保留。连点两个零件时只算后一个 |
| T2 | transition | 聚焦：观察 → 聚焦视图 | 三个入口：`#focus-control` 里选 主视/俯视/左视（`sui-segment-item` 的 `select`）；点图纸上的 `#sheet-view-front｜top｜left`（或聚焦后按 Enter）；点箱里的 `#vol-plane-front｜top｜left`。三处状态同步，摊平时也都可用 |
| T3 | transition | 取消聚焦：聚焦视图 → 观察 | `#focus-control` 选"全部 All"（摊平时同样有效，状态留在 `unfolded`） |
| T4 | transition | 展开：观察/聚焦 → 摊平 | 点 `#unfold-toggle`（`checked-change`）→ `setUnfolded(true)`；从聚焦进入时聚焦保留 |
| T5 | transition | 合上：摊平 → 观察 | 再点 `#unfold-toggle` → `setUnfolded(false)`。先整组和视角归位，再两个面折回、零件同时回到三面角，最后投影线出现。带着聚焦合上，回到的是"聚焦视图" |
| T6 | transition | 开关投影线 / 虚线 / 对齐线 | 投影箱工具条：`#rays-toggle` → `data-rays`（`.ray` 显隐，摊平时 disabled、取值保留）；`#hidden-toggle` → `data-hidden-lines`（图纸和三个面上的 `.ln-h` 显隐）。图纸工具条：`#align-toggle` → `data-align`（图纸上的 `#sheet-align` 显隐），除视图计算失败外都可用 |
| T7 | transition | 复位视角 | 点 `#reset-view`（`click-action`）→ `--yaw` 回到当前状态的默认值（折叠 28°，摊平 0°） |
| T8 | transition | 开 / 关零件库窗口 | 工具条 `#parts-toggle`（`checked-change`）双向开关；窗口自己的系统关闭键 `#parts-close`（`click-action`）关闭，并把 `#parts-toggle` 置为未选中 |
| T9 | transition | 开 / 关图纸窗口 | 工具条 `#sheet-toggle` 双向开关；窗口自己的系统关闭键 `#sheet-close` 关闭，并把 `#sheet-toggle` 置为未选中 |
| T10 | transition | 计算失败 / 重试：任意 → 视图计算失败 → 原状态 | 样例错误数据：选零件 08"开口圆筒 Split Tube"时第一次计算返回错误（图纸关着就先打开）；点 `#compute-retry` 重算成功，回到进入前的状态（聚焦、展开都保留）；换一个零件也能离开。脚本入口 `threeViewsPreview.failNextCompute()` 可让任意零件的下一次计算失败 |
| C1 | component | 零件库行（默认 / 悬停 / 选中） | `#part-list` 里的 8 个 `.part-row`（`sui-side-navigation-item`）：编号、中文名 + 英文名、中文说明、英文说明、难度（中英两行）；选中 = `selected` 属性 + `fillPrimary` 底 + `.part-dot` 圆点；悬停是组件自带的 `fillLight` |
| C2 | component | 图纸（显示/隐藏虚线、显示/隐藏对齐线、某视图高亮、正在计算、错误占位；视图名称、标题栏） | `#sheet-paper` 里的 `#sheet-svg`：`#sheet-view-front`（左上）、`#sheet-view-left`（右上）、`#sheet-view-top`（主视图正下方）、`#sheet-axes`、`#sheet-miter`（45° 线）、`#sheet-align`、`#sheet-title-block`（名称 Name、比例 Scale、图号 No.、单位 Unit、第一角画法符号）；`#sheet-loading`；`#sheet-error` |
| C3 | component | 视图聚焦分段控件 | `#focus-control`（全部 All / 主视 Front / 俯视 Top / 左视 Left），每段宽 56、高 48 |
| C4 | component | 展开开关 | `#unfold-toggle` |
| C5 | component | 投影线、虚线、对齐线开关 | `#rays-toggle` `#hidden-toggle` 在 `#box-toolbar`；`#align-toggle` 在 `#sheet-toolbar` |
| C6 | component | 复位按钮 | `#reset-view` |
| C7 | component | 窗口开关（第 2 轮新增，取代"投影箱已关闭提示条"） | `#parts-toggle` `#sheet-toggle`，以及两个窗口的系统关闭键 `#parts-close` `#sheet-close` |
| C8 | component | 投影箱（3D）：零件、三面、投影线、展开、聚焦、转角 | `#projection-box`：`#vol-plane-front`（V，后）、`#vol-plane-top`（H，下）、`#vol-plane-left`（W，右）、`#vol-part`（`.face`）、`#vol-rays`（`.ray-front｜top｜left`）；横向拖动 `#vol-viewport` 改 `--yaw`。面上的名牌："V 主视图 Front""H 俯视图 Top""W 左视图 Left" |
| B1 | binding | 零件库（编号、名称、结构说明、难度） | 正常：`PARTS` 的 01–06、08，每项有中英名称、中英说明、中英难度。缺失：07"支座 Bearing Seat"没有说明和难度，行里显示"暂无结构说明 / No description yet"和"—"。错误：无（库是本地数据） |
| B2 | binding | 当前零件的三视图线集（可见、不可见、中心线）+ 外接尺寸 | 正常：`part.views`，图纸和三个投影面用同一份数据、同一个绘制函数。计算中：`#sheet-loading`。缺失：`threeViewsPreview.setLineData('missing')`，三个视图区显示"暂无视图数据 No view data"，比例显示"—"，"投影线"和"虚线"开关 disabled。错误：零件 08 的首次计算，或 `failNextCompute()` → S4 |
| B3 | binding | 聚焦的视图 | `state.focus` ↔ `sui-segment-item[selected]` ↔ `.is-focus` / `.is-dim`。缺省值"全部" |
| B4 | binding | 是否展开 | `state.unfolded` ↔ `#unfold-toggle[checked]` ↔ `#projection-box[data-fold]`。缺省值"折叠" |
| B5 | binding | 投影线 / 虚线 / 对齐线三个布尔值 | `state.rays` / `state.hiddenLines` / `state.align` ↔ 三个开关的 `checked` ↔ `#room` 上的三个 data 属性。缺省值 开 / 开 / 关 |
| B6 | binding | 两个 Planar 窗口是否打开 | `state.partsOpen` ↔ `#parts-slot[hidden]` ↔ `#parts-toggle[checked]`（缺省 开）；`state.sheetOpen` ↔ `#sheet-slot[hidden]` ↔ `#sheet-toggle[checked]`（缺省 关） |
| B7 | binding | 零件网格 | `part.solids`（棱柱 + 圆孔）→ `#vol-part` 里的 `.face`；计算失败时网格照常显示；摊平时同一个网格作为立体图留在第四象限 |
| B8 | binding | 绕竖轴的转角 | `state.yaw` ↔ `#vol-camera` 的 `--yaw`；拖动改值，复位回默认 |
| B9 | binding | 三等关系对齐是否成立 | 由线集算出三组坐标（x、z、y），在图纸上画对齐线；成对的两个视图在该组坐标上一致时，标签前显示 `passable` 色的勾（`.align-ok`） |

**中英双语** — 每个用户可见的标签都是中文配英文。

| 位置 | 写法 |
| --- | --- |
| 按钮、开关、分段（两行：中文在上，较小的英文在下，`.bi`） | 全部 All、主视 Front、俯视 Top、左视 Left、展开 Unfold、投影线 Rays、虚线 Hidden、复位 Reset、零件库 Parts、图纸 Sheet、对齐线 Guides、重试 Retry |
| 零件库标题 | "零件库 Parts"，"8 个组合体 · 8 solids" |
| 零件行 | 中文名 + 旁边 `labelSecondary` 色的英文名；说明先中文一行，再英文一行；难度 易 Easy / 中 Medium / 难 Hard |
| 投影箱里的面名牌 | V 主视图 Front、H 俯视图 Top、W 左视图 Left |
| 图纸上的视图名 | 主视图 Front view、俯视图 Top view、左视图 Left view |
| 三等关系标签（两行） | 长对正 Lengths align、高平齐 Heights level、宽相等 Widths equal |
| 标题栏 | 名称 Name、比例 Scale、图号 No.、单位 Unit、第一角画法 First-angle；零件名中英两行 |
| 计算中 / 失败 / 无数据 | 正在计算视图… Computing the views…；这个零件的视图没有算出来 The views of this part could not be computed；暂无视图数据 No view data |

工具条、面名牌、视图名、三等关系、标题栏、计算中、失败标题、重试、"暂无结构说明"这些字符串取自应用工程的 `strings.xml`；失败说明的第二句、"暂无视图数据 No view data"和两个关闭键的无障碍标签是预览里加的。零件的英文名取应用里的名字，只有 08 用了"Split Tube"，因为预览里的 08 是前壁开竖缝的圆筒，和应用里顶部开槽的"Notched Tube"不是同一个形体。英文说明是按预览里的形体写的。

**三视图数据** — 8 个零件的线集都是手写的，国标第一角画法：主视图在左上，左视图在右上，俯视图在主视图正下方。可见轮廓 `.ln-v` 粗实线，不可见轮廓 `.ln-h` 细虚线，轴线和对称中心线 `.ln-c` 细点画线（`CenterLine` 色），对齐线和 45° 线 `.ln-align` / `.ln-miter`（`RayBlue` 色，只在图纸上），投影箱里的投影线 `.ray`（`RayBlue` 色）。可见线最后画，与虚线、点画线重合处显示粗实线。启动时 `verifyPart()` 对每个零件核对"长对正、高平齐、宽相等"三组坐标，8 个都一致。这一轮没有改图形数据。

- 01 L 形块：左视图里底板顶面被立板挡住，画虚线。
- 02 阶梯块：三级台面，两条台阶线在俯视图和左视图里都可见。
- 03 斜切块：斜面下棱在左视图里画虚线。
- 04 带孔底板：俯视图一个圆加十字中心线；主视图、左视图各两条虚线加一条点画线。
- 05 V 形块：槽底线在俯视图可见，在左视图画虚线。
- 06 开槽块：槽沿左右方向贯通，槽底在主视图画虚线。
- 07 支座：底板加带横孔的立板，孔在主视图是圆，在俯视图、左视图是虚线加点画线。
- 08 开口圆筒：俯视图是 C 形；主视图里缝的两条边可见，内孔画虚线；左视图前缘是缝口棱线，内孔后素线和缝的内棱画虚线。

**尺寸和响应式** — 房间是三列网格，左右两列等宽，所以投影箱始终在水平正中，不管两侧窗口开没开。三个表面底边对齐，工具条挂在底边下方 12 px。

| 视口宽度 | 零件库窗口 | 投影箱 | 图纸窗口 | 排法 |
| --- | --- | --- | --- | --- |
| 1920 | 320 × 540 | 416 × 540 | 720 × 540（最小值） | 三个并排，投影箱居中 |
| 2624 | 360 × 720（默认值） | 640 × 720 | 960 × 720（默认值） | 三个并排，投影箱居中 |
| 3936 | 480 × 960（最大值） | 640 × 960 | 1600 × 1200（最大值） | 三个并排，投影箱居中 |
| 1863 及以下 | 360 × 720 | 560 × 720 | 960 × 720 | 零件库 + 投影箱一行，图纸换到下面一行 |
| 752（页面最小宽度） | 320 × 540 | 384 × 540 | 720 × 540 | 同上；视口再窄就横向滚动 |

图纸随纸面缩放，纸面文字（轴名的下标除外）渲染后不小于 12 px。`prefers-reduced-motion` 下所有过渡关闭，展开一步到位。页面上没有视口切换、数据模式切换之类的评审控件。

**Build 阶段做出的取舍**

- 1920 px 宽的视口放不下"默认尺寸的三个表面 + 投影箱居中"：投影箱居中要求左右两侧等宽，图纸默认 960 dp 时整排需要 2624 px。所以 1920 下图纸取最小值 720 × 540，零件库 320 × 540，投影箱 416 dp 宽；默认尺寸在 2624 px 宽的视口（并排）或 1863 px 以下（图纸换行）出现。
- 投影箱工具条宽 793，比投影箱（416–640）宽，所以挂在三个表面共同的底边下方，没有压在投影箱的下沿上；图纸工具条同高挂在图纸下方。视口窄于约 841 px 时，库给工具条定的最大宽度（`100vw - 48px`）会让它在自己内部横向滚动。
- 两个 Planar 窗口的系统关闭键画在窗口上方，因为下方是工具条。这是预览里对系统窗口栏的示意，位置不代表真机。投影箱是主窗口，应用界面里没有它的关闭入口。
- 工具条里的按钮和开关用 `size="small"`（左右内边距 16），高度由两行标签撑到 56；用 `size="max"` 的话工具条会超过库的 960 上限。分段每段 56 × 48，外层容器高 56。
- 英文小字 12 px；零件行里难度的英文是 10 px，低于 12 dp。
- 零件行现在是三行文字（约 78 dp 高），8 行需要约 650 dp。窗口高 720（默认）时一屏放得下，540（1920 视口）时列表内部滚动。
- 加了"正在计算"的短暂阶段（0.32 秒）：只影响图纸，投影箱保留上一个零件，状态名不变。它不在 R4 列的四个状态里，是数据的加载态。脚本入口 `threeViewsPreview.setComputeDelay(ms)` 可以改时长。
- `sui-toolbar` 只在宿主改变尺寸或窗口 resize 时重新定位，宿主被别的窗口挤动时不会跟。所以开关窗口之后脚本补发一次 `resize` 事件。
- 摊平状态下"投影线"开关是 disabled（取值保留，合上后恢复）。
- 摊平时聚焦可用、合上后保留聚焦；展开和合上时视角自动转到该状态的默认角度（摊平 0°，折叠 28°）。
- 投影面取 150 × 110 × 110 mm（模型单位），摊平后 260 × 220，按同一比例画在投影箱里，所以折叠时的三面角只占箱体的一部分。
- 未选中的零件行是组件默认的透明底，没有用 §3 表里写的 `fillSecondary`（组件没有容器色属性）。
- §3 的"Surface sizing""Layout""State graph""Components"几节还是两个表面、五个状态、单语标签的写法，这一轮没有改；以本节和 component map 为准。

**已做的检查** — 用无头 Chrome 154 加载 `preview.html`，用真实鼠标事件点过上表的每个状态、转换和两个窗口开关的两个方向（工具条开关、窗口自己的关闭键），共 113 项，全部通过；控制台没有报错或警告。量过 752、1280、1600、1863、1864、1920、2624、3936 px 宽的视口：表面和工具条互不重叠，尺寸都在各自的最小值和最大值之间，1864 px 及以上三个并排且投影箱中心与视口中心相差不超过 1 px。用到的 10 个 `sui-*` 标签都已注册并在 `window.SUI_COMPONENTS` 里；light DOM 里没有原生 `button` / `input` / `select` / `textarea` / `dialog`。没有在头显上验证。

## 6 · Critique again

**Independent coverage rebuild** — 从 §3 重新列出 5 个状态、10 个转换、8 个组件、9 个数据绑定，对照 `preview.html` 逐项核对：标记里用到 9 个 `sui-*` 标签，脚本里再创建 `sui-side-navigation-item` 和 `sui-dot-badge`，共 11 个，都在组件映射表里；light DOM 里没有原生 `button` / `input` / `select` / `textarea` / `dialog`；没有外部引用；脚本里 5 个状态名（`observe` / `focused` / `unfolded` / `volume-closed` / `compute-error`）齐全；§5 列出的 20 个选择器在文件里都找得到。Build 阶段的无头浏览器检查（38 + 14 + 55 项）用真实鼠标事件点过每个状态和转换，控制台无报错。清单与原型之间没有缺项。

原型与 §3 之间留下的四处差别，以 §3 为准，由应用实现：再点一次已聚焦的视图回到"全部"（原型保持聚焦）；转角限制在 ±80°，摊平时不转动（原型两种状态都能拖）；摊平时"复位"不可用（原型把转角复位到 0°）；展开是一段 0.9 秒的连续动作（原型分两段）。这四处都是同一转换内的手感细节，不增减状态、组件或数据绑定。

**Quality bar**

| Criterion                                                                        | Met? | Evidence |
| -------------------------------------------------------------------------------- | ---- | -------- |
| App's job is a clear decision/task outcome                                       | 是 | 一个任务：看一个零件怎样变成三视图，并对着零件读懂图上每条线 |
| Form/scale follows the spatial justification                                     | 是 | 3D 关系在 Volumetric 投影箱里，读图在 Planar 窗口里，都在 Shared Space |
| Spatialization justified affordance-by-affordance                                | 是 | §1 四行，每行有 2D 对照；选零件和读图明确保留为 2D |
| ≥3 alternatives compared, rejects have reasons                                   | 是 | §2 的 A / B / C，独立上下文生成，B、C 的落选理由是具体事实 |
| Every window/surface records final default/min/max dimensions                    | 是 | §3 Surface sizing：窗口 1280×800（960×640 – 1920×1200）dp，投影箱 1.0 m 立方 |
| Design derives from domain (survives domain-swap)                                | 是 | 投影面、线型、投影轴、45° 线、三等关系、标题栏、第一角画法符号都是制图专属 |
| SpatialUI bundle is inline; mapped tags are registered, present, and event-wired | 是 | 两个 `<script>`，第一个是完整的 bundle；11 个标签都注册并通过公开事件接线 |
| No unjustified native/hand-rolled duplicate of an available `sui-*` control      | 是 | 自绘的只有图纸（SVG）和投影箱（CSS 3D），目录里没有对应组件 |
| Root surface uses SpatialUI Web `vibrant` + `Material.Regular` glass             | 是 | `#planar-window` 用库的 `--Background-MaterialRegular` 变量；纸面是窗口内的卡片，不是窗口根 |
| No design/debug-only controls appear in the product UI                           | 是 | 页面上没有视口、数据模式之类的评审控件；脚本入口只在 `window.threeViewsPreview` 上 |
| preview.html is a triggerable state machine, full coverage                       | 是 | 见上面的独立核对和 §5 |
| Signature present, surroundings quiet                                            | 是 | 展开：H 面下翻、W 面右翻，三面摊成图纸，零件落到第四象限；其余只有列表、图纸和一条工具条 |

**Patch log** (max 3 rounds)

| Round | Problem | Target | Expected improvement | Re-ran Build+critique? |
| ----- | ------- | ------ | -------------------- | ---------------------- |
| 1 | 摊平时零件淡出，用户没法再对着零件读图，而这正是应用的任务 | 投影箱的摊平状态（`#vol-part`） | 零件落到第四象限成为轴测参考，摊平后仍能对照 | 是 |
| 1 | 摊平的箱子上再画一遍对齐线和 45° 线，会从没有投影面的象限里穿过零件背后，而且 2D 图纸上已经有同一组线 | `#vol-flat` | 摊平的箱子上只有三个面和零件；对齐线只作用于图纸 | 是 |
| 1 | 摊平时分段控件被禁用，可零件还在，聚焦一个视图仍然有意义 | `#focus-control`、摊平的投影面 | 摊平时照常聚焦，展开不清掉聚焦 | 是 |
| 1 | 单字的分段宽 40 dp，低于 56 dp 的目标 | `#focus-control` 的标签 | 标签改为 全部 / 主视 / 俯视 / 左视，每段宽 56 dp；960 dp 最小窗口下工具条宽 886 dp，放得下 | 是 |
| 1 | 模拟器证据：0.7 m 的固定尺度体积放在窗口旁边，零件只占视野约 6°，线看不清 | §3 Surface sizing（投影箱） | 体积改为 1.0 m 立方，改用系统的动态尺度，离远了也读得清 | 尺寸不由 Web 原型验证；在模拟器上复核 |

| 2 | 用户在模拟器里启动应用后看不到 3D 投影箱：图纸窗口占了正前方，投影箱被系统放在它右边、视野之外 | §3 Surface sizing、Layout：表面的主次和排布 | 投影箱改为主窗口、居中；零件库拆成左侧的窄窗口，图纸拆成右侧按需打开的窗口；工具条移到投影箱下方 | 是 |
| 2 | 用户要求界面中英双语 | 所有标签 | 按钮、开关、分段两行（中文在上、英文在下）；零件行、名牌、视图名、三等关系、标题栏都带英文 | 是 |
| 2 | 投影箱成了主窗口，"投影箱已关闭"状态和提示条不再成立 | State graph | 去掉该状态和提示条；两个 2D 窗口的开合改由工具条上的两个开关表示 | 是 |
| 2 | 模拟器证据：体积窗口上下留白多，工具条离内容远；底板在投影箱下方多出一大块灰色 | §3 Surface sizing（投影箱） | 体积改为 0.8 × 0.72 × 0.9 m，内容按姿态占满；不显示底板 | 尺寸不由 Web 原型验证；已在模拟器上复核 |

第二轮通过，没有用到第三轮。

第二轮后原型与 §3 之间留下的差别，以 §3 为准，由应用实现：原型在 1920 px 视口下为了让投影箱居中，把两个 2D 窗口压到了各自的最小尺寸；分段控件在原型里是四个分段项放在一个布局容器里（库的分段控件只支持单行文字），应用里用的是 SpatialUI 的 SegmentControl；零件 07、08 的几何在原型里是手写的样例，应用里由算法从实体算出。

**Device validation** — `not_performed`（Web 原型只验证逻辑和排布；真机上的舒适度、遮挡、物理尺寸和性能没有验证）。

**Verdict** — pass。两轮补丁后：4 个状态、11 个转换、9 个组件全部可触发（Build 阶段第二轮的无头浏览器检查 113 项全过）；标准控件都用 SpatialUI Web 组件；投影箱居中、三个表面并排不重叠。仍未达到的两点如实记录：难度的英文小字在原型里是 10 px，低于 12 dp 的下限；1920 px 视口下两个 2D 窗口是最小尺寸而不是默认尺寸。
