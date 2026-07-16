# anime47-desktop

Build sẵn 3 project, chạy tự động qua GitHub Actions — không cần máy
tính mạnh, làm được từ điện thoại.

## Việc cần làm ngay bây giờ

### 1. Tạo repo GitHub mới

Trên app GitHub (điện thoại) hoặc github.com:
- Tạo repo mới, ví dụ tên `anime47-desktop`
- Có thể để **Private** nếu không muốn công khai

### 2. Đẩy toàn bộ thư mục này lên repo đó

Cấu trúc phải giữ nguyên như hiện tại:
```
anime47-desktop/
  .github/workflows/build-desktop.yml   <- workflow, đừng đổi tên/vị trí
  cloudstream-master/                    <- source CloudStream gốc
  provider-jvm/                          <- Anime47Provider đã port
  cs3-desktop-host/                      <- app chạy
```

Nếu chưa quen git trên điện thoại, cách dễ nhất: vào repo trên
github.com → "Add file" → "Upload files" → kéo thả cả 3 thư mục vào
(GitHub web hỗ trợ kéo thả cả folder). Nhớ giữ đúng đường dẫn
`.github/workflows/build-desktop.yml` — nếu web upload làm lệch cấu
trúc, tạo thủ công đúng đường dẫn đó rồi paste nội dung file vào.

### 3. Chạy build

Sau khi đẩy code lên (workflow có `push: branches: [main]` nên tự chạy
ngay), hoặc vào tab **Actions** trên repo → chọn "Build Anime47
Desktop" → nút **Run workflow** để chạy thủ công.

### 4. Đợi và tải kết quả

Build mất khoảng 5-10 phút (phần lớn thời gian là cài Android SDK cho
bước 1). Khi xong, vào Actions → chọn lần chạy vừa xong → phần
**Artifacts** ở cuối trang có:
- `anime47-desktop-jars` — chứa `anime47provider.jar` +
  `cs3-desktop-host.jar`, chạy được ngay trên máy có cài Java 17+
  (`java -jar cs3-desktop-host.jar anime47provider.jar "tên phim"`)
- `anime47-desktop-exe` — chứa file `.exe` Windows thật, không cần
  cài Java

### 5. Nếu build lỗi

Bấm vào bước bị đỏ (X) trong Actions để xem log lỗi, copy đoạn lỗi đó
gửi lại — vì đây là lần build đầu tiên (source được viết mà chưa test
thật do môi trường soạn thảo không build được), có khả năng cần vá
thêm 1-2 chỗ nhỏ.

## Chi tiết kỹ thuật (đã port những gì, giới hạn ra sao)

Xem `cs3-desktop-host/README.md` để biết đầy đủ:
- Đã sửa gì trong `Anime47Provider.kt`
- Cách xử lý server Hydrax/Abyss (cần proxy local riêng)
- Giới hạn hiện tại (console app, chưa có giao diện cửa sổ)
