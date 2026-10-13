# 第三方组件声明

本项目包含以下第三方开源组件，按各自许可证使用。

---

## PasscodeView

来源：https://github.com/hanks-zyh/PasscodeView
许可证：Apache License 2.0
版权：Copyright 2017 hanks

内置文件（相对路径）：

- `app/src/main/java/com/jianji/note/widget/passcode/PasscodeView.java`
- `app/src/main/java/com/jianji/note/widget/passcode/CircleView.java`
- `app/src/main/res/layout/pcv_layout_passcode_view.xml`
- `app/src/main/res/values/pcv_attrs.xml`
- `app/src/main/res/values/pcv_styles.xml`
- `app/src/main/res/values/pcv_colors.xml`
- `app/src/main/res/drawable/pcv_*.xml`

本项目的修改（按 Apache-2.0 第 4(b) 条声明）：

1. 包名由 `com.hanks.passcodeview` 改为 `com.jianji.note.widget.passcode`。
2. `android.support.annotation.*` 迁移到 `androidx.annotation.*`；布局中的
   `android.support.v4.widget.Space` 改为普通占位 `View`（AndroidX 无对应控件）。
3. 资源全部加 `pcv_` 前缀以避免与主工程资源冲突，属性名同步加 `pcv` 前缀。
4. `onFail` 回调原先只在「设置密码的二次输入」阶段触发，导致校验模式输错后
   既不回调也无法清空已输入的圆点。现改为两种模式都会回调，并新增
   `resetInput()` 供调用方在输错后重置输入。
5. `tintImageView` 增加空判断，避免 `getDrawable()` 返回 null 时崩溃。

---

## AndroidX / Material Components / Kotlin

通过 Gradle 依赖引入，均为 Apache License 2.0，未修改源码。
