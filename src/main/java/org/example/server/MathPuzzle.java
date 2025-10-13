package org.example.server;

import java.util.List;

public class MathPuzzle {
    private double target;
    private List<Integer> numbers;
    private String targetDisplay;  // Để hiển thị dưới dạng phân số nếu cần
    private int difficultyLevel;  // Thêm độ khó để điều chỉnh hiển thị

    public MathPuzzle(double target, List<Integer> numbers, int difficultyLevel) {
        this.target = target;
        this.numbers = numbers;
        this.difficultyLevel = difficultyLevel;

        // Tạo chuỗi hiển thị cho target tùy theo độ khó
        formatTargetDisplay();
    }

    // Constructor cũ, giữ lại để tương thích
    public MathPuzzle(double target, List<Integer> numbers) {
        this(target, numbers, 0); // Mặc định là độ khó 0 (hiển thị tự động)
    }

    private void formatTargetDisplay() {
        switch (difficultyLevel) {
            case 1: // Độ khó 1: Chỉ số tự nhiên
                targetDisplay = String.valueOf((int)target);
                break;

            case 2: // Độ khó 2: Phân số hoặc số thập phân 1 chữ số
                if (target == (int)target) {
                    // Số nguyên
                    targetDisplay = String.valueOf((int)target);
                } else {
                    // Kiểm tra xem có thể biểu diễn dạng phân số đơn giản
                    if (isSimpleFraction(target)) {
                        targetDisplay = formatAsFraction(target, true); // true = ưu tiên phân số
                    } else {
                        // Số thập phân 1 chữ số
                        targetDisplay = String.format("%.1f", target).replace(",", ".");
                    }
                }
                break;

            case 3: // Độ khó 3: Số thập phân 2 chữ số hoặc hỗn số
                if (target == (int)target) {
                    // Số nguyên
                    targetDisplay = String.valueOf((int)target);
                } else {
                    double fraction = target - Math.floor(target);

                    if (isSimpleFraction(fraction) && Math.floor(target) > 0) {
                        // Hiển thị dạng hỗn số
                        targetDisplay = formatAsFraction(target, false); // false = ưu tiên hỗn số
                    } else {
                        // Số thập phân 2 chữ số
                        targetDisplay = String.format("%.2f", target).replace(",", ".");
                        // Xóa số 0 ở cuối nếu có
                        if (targetDisplay.endsWith("0")) {
                            targetDisplay = targetDisplay.substring(0, targetDisplay.length() - 1);
                        }
                    }
                }
                break;

            default: // Tự động nhận dạng
                if (target == (int)target) {
                    // Số nguyên
                    targetDisplay = String.valueOf((int)target);
                } else {
                    double fraction = target - Math.floor(target);

                    // Kiểm tra xem có phải là phân số đơn giản không
                    if (isSimpleFraction(fraction)) {
                        targetDisplay = formatAsFraction(target, false);
                    } else {
                        // Số thập phân
                        targetDisplay = String.format("%.2f", target).replace(",", ".");
                        // Xóa số 0 ở cuối nếu có
                        if (targetDisplay.endsWith("0")) {
                            targetDisplay = targetDisplay.substring(0, targetDisplay.length() - 1);
                            if (targetDisplay.endsWith("0")) {
                                targetDisplay = targetDisplay.substring(0, targetDisplay.length() - 2);
                            }
                        }
                    }
                }
                break;
        }
    }

    // Kiểm tra xem một số có phải là phân số đơn giản không (1/2, 1/4, 3/4, vv)
    private boolean isSimpleFraction(double fraction) {
        // Kiểm tra các phân số phổ biến
        double[] commonFractions = {
                1.0/2, 1.0/3, 2.0/3, 1.0/4, 3.0/4,
                1.0/5, 2.0/5, 3.0/5, 4.0/5,
                1.0/6, 5.0/6, 1.0/8, 3.0/8, 5.0/8, 7.0/8
        };

        for (double cf : commonFractions) {
            if (Math.abs(fraction - cf) < 0.0001) {
                return true;
            }
        }

        // Kiểm tra thêm xem có thể biểu diễn dạng phân số đơn giản không
        int denom = 100;
        int num = (int)Math.round(fraction * denom);
        int g = gcd(num, denom);

        // Nếu mẫu sau khi rút gọn <= 10 thì coi là phân số đơn giản
        return denom / g <= 10;
    }

    // Định dạng số thành phân số hoặc hỗn số
    private String formatAsFraction(double number, boolean preferSimpleFraction) {
        int wholePart = (int) number;
        double fraction = number - wholePart;

        // Nếu số là nguyên
        if (fraction == 0) {
            return String.valueOf(wholePart);
        }

        // Đổi phần phân số thành phân số tối giản
        int precision = 100000;
        int num = (int)Math.round(fraction * precision);
        int den = precision;
        int gcd = gcd(num, den);
        num = num / gcd;
        den = den / gcd;

        // Xử lý các phân số đơn giản
        if (preferSimpleFraction || wholePart == 0) {
            // Ưu tiên phân số đơn giản a/b
            if (wholePart != 0) {
                num = wholePart * den + num;
            }
            return num + "/" + den;
        } else {
            // Ưu tiên hỗn số e a/b
            return wholePart + " " + num + "/" + den;
        }
    }

    // Tìm ước chung lớn nhất
    private int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (b == 0) return a;
        return gcd(b, a % b);
    }

    public double getTarget() {
        return target;
    }

    public String getTargetDisplay() {
        return targetDisplay;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }

    @Override
    public String toString() {
        return "Target: " + targetDisplay + "\nSố: " + numbers.toString();
    }
}