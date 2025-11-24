package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyController {
    private static final Logger logger = LoggerFactory.getLogger(PolicyController.class);

    @FXML
    private TextArea policyTextArea;

    @FXML
    private Button backButton;

    @FXML
    public void initialize() {
        if (policyTextArea != null) {
            policyTextArea.setWrapText(true);
            policyTextArea.setEditable(false);
            policyTextArea.setText(
                "\n" +
                "1. Giới thiệu\n" +
                "Math Speed Game tôn trọng và cam kết bảo vệ quyền riêng tư của bạn. " +
                "Chính sách này mô tả cách chúng tôi thu thập, sử dụng và bảo vệ thông tin cá nhân của người dùng.\n" +
                "\n" +
                "2. Thông tin chúng tôi thu thập\n" +
                "- Thông tin tài khoản: tên đăng nhập, mật khẩu đã mã hóa, tên hiển thị.\n" +
                "- Thông tin hồ sơ: avatar, giới tính, quốc gia.\n" +
                "- Dữ liệu sử dụng: điểm số, thành tích, số trận chơi, thống kê liên quan đến quá trình chơi.\n" +
                "\n" +
                "3. Mục đích sử dụng thông tin\n" +
                "Chúng tôi sử dụng thông tin của bạn để:\n" +
                "- Tạo và duy trì tài khoản của bạn.\n" +
                "- Lưu trữ và hiển thị thành tích, bảng xếp hạng và tiến độ học tập.\n" +
                "- Cải thiện trải nghiệm người dùng và chất lượng trò chơi.\n" +
                "\n" +
                "4. Chia sẻ và bảo mật dữ liệu\n" +
                "- Chúng tôi KHÔNG bán hoặc cho thuê thông tin cá nhân của bạn cho bên thứ ba.\n" +
                "- Dữ liệu chỉ được chia sẻ trong phạm vi cần thiết để vận hành dịch vụ (ví dụ: hạ tầng máy chủ, lưu trữ).\n" +
                "- Chúng tôi áp dụng các biện pháp kỹ thuật hợp lý để bảo vệ dữ liệu khỏi truy cập trái phép.\n" +
                "\n" +
                "5. Quyền của người dùng\n" +
                "Bạn có quyền:\n" +
                "- Xem và cập nhật một số thông tin hồ sơ của mình bên trong ứng dụng.\n" +
                "- Yêu cầu xóa tài khoản và dữ liệu liên quan (trong phạm vi cho phép của hệ thống).\n" +
                "\n" +
                "6. Lưu trữ dữ liệu\n" +
                "Dữ liệu sẽ được lưu trữ trong thời gian tài khoản của bạn còn hoạt động. " +
                "Sau khi tài khoản bị xóa, chúng tôi sẽ xóa hoặc ẩn danh dữ liệu trong một khoảng thời gian hợp lý.\n" +
                "\n" +
                "7. Thay đổi chính sách\n" +
                "Chúng tôi có thể cập nhật chính sách này theo thời gian. Phiên bản mới nhất sẽ được hiển thị trong ứng dụng. " +
                "Việc tiếp tục sử dụng ứng dụng sau khi chính sách được cập nhật đồng nghĩa với việc bạn đồng ý với các thay đổi đó.\n" +
                "\n" +
                "8. Liên hệ\n" +
                "Nếu bạn có câu hỏi hoặc yêu cầu liên quan đến quyền riêng tư, vui lòng liên hệ qua email hỗ trợ:\n" +
                "support@mathspeed.example\n"
            );
        }
    }

    @FXML
    private void handleBack() {
        try {
            SceneManager.getInstance().navigate(SceneManager.Screen.PROFILE);
        } catch (Exception ex) {
            logger.warn("Failed to navigate back to profile", ex);
        }
    }
}
