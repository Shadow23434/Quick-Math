package org.example.server;

import java.util.*;
import java.util.regex.*;

/**
 * Lớp xử lý và đánh giá biểu thức toán học, so sánh với target
 */
public class MathExpressionEvaluator {

    /**
     * Kiểm tra xem biểu thức có chính xác bằng giá trị target hay không
     *
     * @param expression Biểu thức toán học cần đánh giá
     * @param allowedNumbers Danh sách các số được phép sử dụng trong biểu thức
     * @param target Giá trị target cần đạt được
     * @return true nếu biểu thức hợp lệ và bằng target, false trong trường hợp ngược lại
     */
    public boolean isExpressionValid(String expression, List<Integer> allowedNumbers, double target) {
        try {
            // Chuẩn hóa biểu thức (loại bỏ khoảng trắng, xử lý phép chia, ...)
            expression = normalizeExpression(expression);

            // Kiểm tra xem biểu thức chỉ sử dụng các số cho phép
            if (!validateNumbersUsed(expression, allowedNumbers)) {
                return false; // Sử dụng số không được phép
            }

            // Parse và đánh giá biểu thức
            Fraction result = parseAndEvaluate(expression);

            // So sánh kết quả với target
            return compareWithTarget(result, target);
        } catch (Exception e) {
            System.err.println("Error evaluating expression: " + e.getMessage());
            return false; // Biểu thức không hợp lệ
        }
    }

    /**
     * Tính toán giá trị của biểu thức và trả về kết quả dưới dạng phân số
     *
     * @param expression Biểu thức toán học cần đánh giá
     * @return Kết quả dưới dạng phân số hoặc null nếu biểu thức không hợp lệ
     */
    public Fraction evaluateExpression(String expression) {
        try {
            // Chuẩn hóa biểu thức
            expression = normalizeExpression(expression);

            // Parse và đánh giá biểu thức
            return parseAndEvaluate(expression);
        } catch (Exception e) {
            System.err.println("Error evaluating expression: " + e.getMessage());
            return null; // Biểu thức không hợp lệ
        }
    }

    /**
     * Chuẩn hóa biểu thức để thuận tiện xử lý
     */
    private String normalizeExpression(String expression) {
        // Loại bỏ khoảng trắng
        expression = expression.replaceAll("\\s+", "");

        // Thay thế dấu ÷ bằng dấu /
        expression = expression.replace('÷', '/');

        // Thay thế dấu × bằng dấu *
        expression = expression.replace('×', '*');

        return expression;
    }

    /**
     * Kiểm tra xem biểu thức chỉ sử dụng các số được phép
     */
    private boolean validateNumbersUsed(String expression, List<Integer> allowedNumbers) {
        // Tạo một bản sao của danh sách số được phép
        List<Integer> availableNumbers = new ArrayList<>(allowedNumbers);

        // Trích xuất tất cả các số từ biểu thức
        List<Integer> usedNumbers = extractNumbers(expression);

        // Kiểm tra từng số sử dụng
        for (Integer num : usedNumbers) {
            if (!availableNumbers.remove(num)) {
                // Số không có trong danh sách hoặc đã sử dụng hết
                return false;
            }
        }

        return true;
    }

    /**
     * Trích xuất tất cả các số từ biểu thức
     */
    private List<Integer> extractNumbers(String expression) {
        List<Integer> numbers = new ArrayList<>();

        // Sử dụng regex để tìm tất cả các số nguyên trong biểu thức
        Matcher matcher = Pattern.compile("\\d+").matcher(expression);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }

        return numbers;
    }

    /**
     * Parse và đánh giá biểu thức toán học, xử lý phân số
     */
    private Fraction parseAndEvaluate(String expression) {
        // Thuật toán Shunting Yard để chuyển biểu thức trung tố sang hậu tố
        List<String> tokens = tokenizeExpression(expression);
        List<String> postfix = infixToPostfix(tokens);

        // Đánh giá biểu thức hậu tố
        return evaluatePostfix(postfix);
    }

    /**
     * Tách biểu thức thành các token
     */
    private List<String> tokenizeExpression(String expression) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentNumber = new StringBuilder();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isDigit(c)) {
                // Thêm chữ số vào số hiện tại
                currentNumber.append(c);
            } else {
                // Kết thúc số hiện tại nếu có
                if (currentNumber.length() > 0) {
                    tokens.add(currentNumber.toString());
                    currentNumber.setLength(0);
                }

                // Xử lý các toán tử và dấu ngoặc
                if (c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')') {
                    tokens.add(String.valueOf(c));
                }
                // Bỏ qua các ký tự khác
            }
        }

        // Thêm số cuối cùng nếu có
        if (currentNumber.length() > 0) {
            tokens.add(currentNumber.toString());
        }

        return tokens;
    }

    /**
     * Chuyển biểu thức trung tố sang hậu tố sử dụng thuật toán Shunting Yard
     */
    private List<String> infixToPostfix(List<String> infix) {
        List<String> postfix = new ArrayList<>();
        Stack<String> operators = new Stack<>();

        Map<String, Integer> precedence = new HashMap<>();
        precedence.put("+", 1);
        precedence.put("-", 1);
        precedence.put("*", 2);
        precedence.put("/", 2);

        for (String token : infix) {
            if (isNumeric(token)) {
                // Số được thêm trực tiếp vào kết quả
                postfix.add(token);
            } else if (token.equals("(")) {
                operators.push(token);
            } else if (token.equals(")")) {
                // Đẩy các toán tử trong ngoặc vào kết quả
                while (!operators.isEmpty() && !operators.peek().equals("(")) {
                    postfix.add(operators.pop());
                }
                // Loại bỏ dấu ngoặc mở
                if (!operators.isEmpty() && operators.peek().equals("(")) {
                    operators.pop();
                } else {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
            } else if (precedence.containsKey(token)) {
                // Xử lý toán tử
                while (!operators.isEmpty() && precedence.containsKey(operators.peek()) &&
                        precedence.get(operators.peek()) >= precedence.get(token)) {
                    postfix.add(operators.pop());
                }
                operators.push(token);
            }
        }

        // Đẩy các toán tử còn lại vào kết quả
        while (!operators.isEmpty()) {
            if (operators.peek().equals("(")) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            postfix.add(operators.pop());
        }

        return postfix;
    }

    /**
     * Kiểm tra xem một token có phải là số không
     */
    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Đánh giá biểu thức hậu tố
     */
    private Fraction evaluatePostfix(List<String> postfix) {
        Stack<Fraction> stack = new Stack<>();

        for (String token : postfix) {
            if (isNumeric(token)) {
                stack.push(new Fraction(Integer.parseInt(token), 1));
            } else {
                // Lấy hai operand từ stack
                if (stack.size() < 2) {
                    throw new IllegalArgumentException("Invalid expression");
                }

                Fraction b = stack.pop();
                Fraction a = stack.pop();

                // Thực hiện phép tính
                switch (token) {
                    case "+":
                        stack.push(Fraction.add(a, b));
                        break;
                    case "-":
                        stack.push(Fraction.subtract(a, b));
                        break;
                    case "*":
                        stack.push(Fraction.multiply(a, b));
                        break;
                    case "/":
                        stack.push(Fraction.divide(a, b));
                        break;
                }
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Invalid expression");
        }

        return stack.pop();
    }

    /**
     * So sánh kết quả biểu thức với target
     */
    private boolean compareWithTarget(Fraction result, double target) {
        // Nếu target là số nguyên, chuyển thành phân số
        if (target == (int)target) {
            Fraction targetFraction = new Fraction((int)target, 1);
            return result.equals(targetFraction);
        }

        // Xử lý target là phân số hoặc số thập phân
        Fraction targetFraction = new Fraction(target);

        // So sánh phân số
        return result.equals(targetFraction);
    }

    /**
     * Class để biểu diễn phân số
     */
    public static class Fraction {
        private int numerator;
        private int denominator;

        public Fraction(int numerator, int denominator) {
            if (denominator == 0) {
                throw new IllegalArgumentException("Denominator cannot be zero");
            }

            // Chuẩn hóa dấu (dấu chỉ ở tử số)
            if (denominator < 0) {
                numerator = -numerator;
                denominator = -denominator;
            }

            // Rút gọn phân số
            int gcd = gcd(Math.abs(numerator), Math.abs(denominator));
            this.numerator = numerator / gcd;
            this.denominator = denominator / gcd;
        }

        public Fraction(double value) {
            // Chuyển số thập phân thành phân số
            convertFromDecimal(value);
        }

        // Chuyển số thập phân thành phân số (độ chính xác đến 6 chữ số)
        private void convertFromDecimal(double value) {
            // Xử lý phần nguyên
            int intPart = (int) value;
            double decPart = Math.abs(value - intPart);

            // Nếu là số nguyên
            if (decPart < 0.000001) {
                numerator = intPart;
                denominator = 1;
                return;
            }

            // Chuyển đổi phần thập phân thành phân số
            final int MAX_DENOMINATOR = 1000000; // Độ chính xác
            int sign = value < 0 ? -1 : 1;

            // Tìm phân số gần nhất
            int bestNumerator = 0;
            int bestDenominator = 1;
            double bestError = Math.abs(value);

            for (int den = 1; den <= MAX_DENOMINATOR; den++) {
                int num = (int)Math.round(Math.abs(value) * den);
                double error = Math.abs(Math.abs(value) - (double)num/den);

                if (error < bestError) {
                    bestNumerator = num;
                    bestDenominator = den;
                    bestError = error;

                    // Nếu sai số đủ nhỏ thì dừng
                    if (error < 0.000001) break;
                }
            }

            // Rút gọn phân số
            int gcd = gcd(bestNumerator, bestDenominator);
            numerator = sign * bestNumerator / gcd;
            denominator = bestDenominator / gcd;
        }

        public static Fraction add(Fraction a, Fraction b) {
            int newNumerator = a.numerator * b.denominator + b.numerator * a.denominator;
            int newDenominator = a.denominator * b.denominator;
            return new Fraction(newNumerator, newDenominator);
        }

        public static Fraction subtract(Fraction a, Fraction b) {
            int newNumerator = a.numerator * b.denominator - b.numerator * a.denominator;
            int newDenominator = a.denominator * b.denominator;
            return new Fraction(newNumerator, newDenominator);
        }

        public static Fraction multiply(Fraction a, Fraction b) {
            int newNumerator = a.numerator * b.numerator;
            int newDenominator = a.denominator * b.denominator;
            return new Fraction(newNumerator, newDenominator);
        }

        public static Fraction divide(Fraction a, Fraction b) {
            if (b.numerator == 0) {
                throw new ArithmeticException("Division by zero");
            }
            int newNumerator = a.numerator * b.denominator;
            int newDenominator = a.denominator * b.numerator;
            return new Fraction(newNumerator, newDenominator);
        }

        // Tìm UCLN bằng thuật toán Euclid
        private static int gcd(int a, int b) {
            while (b != 0) {
                int temp = b;
                b = a % b;
                a = temp;
            }
            return a;
        }

        // Chuyển phân số thành giá trị double
        public double toDouble() {
            return (double) numerator / denominator;
        }

        public int getNumerator() {
            return numerator;
        }

        public int getDenominator() {
            return denominator;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Fraction fraction = (Fraction) obj;
            return numerator == fraction.numerator && denominator == fraction.denominator;
        }

        @Override
        public String toString() {
            if (denominator == 1) {
                return String.valueOf(numerator);
            }
            return numerator + "/" + denominator;
        }
    }
}
