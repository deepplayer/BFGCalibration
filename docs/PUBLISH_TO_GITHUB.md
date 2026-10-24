# 把源码放到 GitHub

## GitHub Desktop（推荐）

1. 打开 GitHub Desktop 并登录 GitHub。
2. 选择 `File → Add Local Repository`，选择本目录。
3. 如果提示本目录还不是 Git 仓库，选择创建/初始化本地仓库。不要把 `local.properties`、签名文件或 APK 加进去。
4. 填写提交说明，例如 `整理 BFG 电量校准源码`，点击 `Commit to main`。
5. 点击顶部 `Publish repository`，仓库名填写 `BFGCalibration`，再选择公开或私有。

## 命令行

在本目录打开 PowerShell，依次运行：

```text
git init
git add .
git commit -m "Prepare BFG calibration 5.0.35 source release"
git branch -M main
git remote add origin https://github.com/<你的账号>/BFGCalibration.git
git push -u origin main
```

把 `<你的账号>` 换成自己的 GitHub 用户名。首次推送按 GitHub 的提示完成登录。推送前可以运行：

```text
git status --ignored
```

确认没有个人日志、车辆凭证、APK、`local.properties` 或 `release-signing.properties`。`.gitignore` 已经配置了这些本地文件的忽略规则，但仍应人工检查。
