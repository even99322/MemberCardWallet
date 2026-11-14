# 會員卡夾 MemberCardWallet

把各家店的會員條碼集中在一個 Android App 裡，結帳時打開就能給店員掃。

## 功能

- **新增會員卡**
  - 相機掃描實體卡或別人手機上的條碼
  - **從截圖辨識**：在店家 App 裡截圖會員條碼，選這張圖就會自動讀出號碼與格式
  - 手動輸入號碼
- **從其他 App 分享匯入**：在店家 App 或其他會員卡 App 截圖／分享條碼 →選「加入會員卡夾」，自動辨識並建立卡片
- **連結店家 App**：卡片頁一鍵開啟店家 App（適合 OPENPOINT、PX Pay 這類動態條碼），在清單長按卡片也能直接開啟
- **主畫面捷徑**：卡片頁按「🏠」把卡片釘到桌面，一點就顯示條碼；長按 App 圖示也會列出最常用的 4 張
- 支援 Code 128、Code 39（手機條碼載具）、EAN-13/8、UPC-A/E、ITF、Codabar、Code 93、QR Code、PDF417、Data Matrix、Aztec，或只顯示號碼
- 常見店家快速帶入（7-ELEVEN、全家、全聯、家樂福、屈臣氏、康是美、星巴克、Costco、IKEA…）
- 顯示條碼時螢幕自動調到最亮並保持常亮
- 可設為「常用」置頂，其餘依最近使用排序；可搜尋店家、號碼、備註
- 自訂卡片顏色、備註
- 匯出／匯入 JSON 備份（換手機時使用），資料只存在手機本機

## 下載安裝

到 [Releases](../../releases) 下載最新的 `MemberCardWallet-x.y.z.apk`，在手機上開啟安裝（需允許「安裝未知來源應用程式」）。

## 開發

- Kotlin + Jetpack Compose（Material 3），minSdk 26（Android 8.0）
- 條碼產生／辨識：ZXing、zxing-android-embedded
- 推送到 `main` 時，GitHub Actions 會編譯、簽章並發布 Release（版本號在 `version.txt`，要出新版就改這個檔）

```bash
./gradlew assembleRelease
```

簽章金鑰放在 repo Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。沒有設定時會使用 `app/signing/release.jks`（repo 內附金鑰）簽章。
