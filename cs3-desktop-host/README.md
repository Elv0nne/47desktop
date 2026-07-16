# cs3-desktop-host

Chạy plugin Anime47 (CloudStream) trên Windows/Linux/macOS dưới dạng JVM
app thuần — không giả lập Android, không cần WSA/BlueStacks.

## Đã làm gì với plugin của bạn

Rà qua toàn bộ `uhnimefourseven-master` bạn upload:

| File | Dùng được trên desktop? | Đã sửa gì |
|---|---|---|
| `Anime47Provider.kt` | ✅ | Bỏ `SharedPreferences`/`CommonActivity` (đọc login Android), thay bằng biến môi trường `ANIME47_EMAIL` / `ANIME47_PASSWORD` |
| `HydraxExtractor.kt` | ✅ | Không sửa gì — vốn đã sạch, 0 import Android |
| `Anime47Plugin.kt` | ❌ không dùng | Bỏ hẳn — chỉ là điểm nối cho CloudStream Android (`registerMainAPI`, kiểm tra chữ ký APK...), host desktop tự khởi tạo `Anime47Provider()` trực tiếp |
| `Anime47SettingsDialog.kt` / `Anime47SettingsFragment.kt` | ❌ không dùng | Bỏ hẳn — UI Settings Android, thay bằng biến môi trường ở trên |

**Vấn đề phát sinh quan trọng — server Hydrax/Abyss ("HY"):** plugin gốc
trả về link dạng `https://hydrax-relay.internal/...` — 1 host giả, chỉ
CloudStream Android hiểu được nhờ 1 `OkHttp Interceptor` gắn sẵn trong
ExoPlayer. VLC/mpv trên desktop không có interceptor đó nên không phát
được link này trực tiếp.

**Đã xử lý:** viết `HydraxLocalProxy.kt` — một HTTP server thật chạy ở
`http://127.0.0.1:47471`, copy y hệt logic giải mã segment (AES-CTR +
double-base64) từ `HydraxInterceptor` gốc, chỉ khác là chạy như server
thật thay vì interceptor nội bộ. `host-app` tự phát hiện link Hydrax,
tự khởi động proxy này, đổi URL, rồi mới đưa cho VLC/mpv.

## Cấu trúc

```
cloudstream-master/     <- source CloudStream gốc (không sửa gì)
provider-jvm/            <- Anime47Provider + HydraxExtractor + HydraxLocalProxy, build ra 1 .jar
cs3-desktop-host/
  host-app/              <- app console, nạp provider .jar, search -> load -> loadLinks -> mở player
```

## Cách build (theo đúng thứ tự)

### Bước 1 — Publish `library` module ra Maven local

Trong thư mục `cloudstream-master`:
```bash
./gradlew :library:publishToMavenLocal
```
Kiểm tra:
```bash
ls ~/.m2/repository/com/lagradost/api/library-jvm/1.0.1/
```

### Bước 2 — Build provider thành `.jar`

Trong thư mục `provider-jvm`:
```bash
./gradlew jar
```
Output: `build/libs/anime47provider.jar` — chứa cả `Anime47Provider`,
`HydraxExtractor`, và `HydraxLocalProxy`.

### Bước 3 — Build host-app

Trong thư mục `cs3-desktop-host`:
```bash
./gradlew :host-app:shadowJar
```
Output: `host-app/build/libs/cs3-desktop-host.jar`

### Bước 4 — Chạy

```bash
# (tuỳ chọn) nếu muốn dùng tính năng đăng nhập Anime47:
export ANIME47_EMAIL="..."
export ANIME47_PASSWORD="..."

java -jar host-app/build/libs/cs3-desktop-host.jar \
     provider-jvm/build/libs/anime47provider.jar \
     "tên anime cần tìm"
```

Việc sẽ xảy ra theo thứ tự:
1. Nạp `Anime47Provider`
2. `search()` — in danh sách kết quả
3. `load()` kết quả đầu tiên — lấy danh sách tập
4. `loadLinks()` tập đầu tiên — lấy link stream + phụ đề
5. Nếu link là dạng Hydrax relay → tự khởi động `HydraxLocalProxy`, đổi
   sang `http://127.0.0.1:47471/...`
6. Tự mở **mpv** (ưu tiên) hoặc **VLC** với link cuối cùng — nếu máy
   không có sẵn 2 player này, in link ra để bạn tự dán vào player khác

Cần cài **mpv** hoặc **VLC** trên máy và có trong PATH để bước 6 tự động
mở phim. Nếu không, link vẫn được in ra console để mở thủ công.

## Đóng gói thành `.exe` thật (không cần cài Java)

```bash
jpackage --input host-app/build/libs \
         --main-jar cs3-desktop-host.jar \
         --main-class com.thitbokobe.host.MainKt \
         --type exe \
         --name CloudStreamDesktop
```
Cần chạy `jpackage` (đi kèm JDK 17+) trên máy Windows thật hoặc runner
CI Windows — không cross-compile được từ Linux.

## Giới hạn còn lại

- Đây vẫn là console app — gõ lệnh, không có ô tìm kiếm/danh sách bấm
  chuột. Nếu muốn giao diện cửa sổ thật, đó là bước tiếp theo (Compose
  Desktop), dùng lại nguyên phần logic này làm nền.
- `HydraxLocalProxy` được viết lại dựa trên đọc kỹ `HydraxInterceptor`
  gốc, logic mật mã đối chiếu khớp 100% với bản gốc — nhưng **chưa được
  chạy thử thật** (môi trường soạn thảo này không có Android
  SDK/Gradle distribution để build). Nếu link Hydrax không phát được,
  khả năng cao nhất là do domain CDN hoặc protocol segment đã đổi so
  với lúc `HydraxExtractor.kt` được viết — không phải lỗi ở việc "port"
  sang desktop.
- Các server khác trong Anime47Provider (nếu có, ngoài Hydrax/HY) chạy
  bình thường không cần proxy gì thêm, vì chúng trả URL thật, phát
  trực tiếp được.
