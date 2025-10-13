package org.example.server;

import java.util.*;

public class MathPuzzleGenerator {
    private static final int MIN_NUMBERS = 5;
    private static final int MAX_NUMBERS = 10;
    private static final Random random = new Random();
    private int difficultyLevel; // 1: Dễ, 2: Trung bình, 3: Khó

    public MathPuzzleGenerator(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    // Class để lưu trạng thái trong BFS
    private static class State {
        double value;
        List<Integer> numbersUsed;
        List<String> operations;

        public State(double value, List<Integer> numbersUsed, List<String> operations) {
            this.value = value;
            this.numbersUsed = new ArrayList<>(numbersUsed);
            this.operations = new ArrayList<>(operations);
        }
    }

    // Sinh một đề toán mới
    public MathPuzzle generatePuzzle() {
        // Tạo target dựa vào độ khó
        double target = generateTarget();

        // Sử dụng BFS để tạo bộ số phù hợp
        List<Integer> numbers = generateNumberSet(target);

        return new MathPuzzle(target, numbers, difficultyLevel);
    }

    // Tạo target theo độ khó
    private double generateTarget() {
        switch (difficultyLevel) {
            case 1: // Dễ: CHỈ số tự nhiên từ 1-20
                return random.nextInt(20) + 1;

            case 2: // Trung bình: Phân số hoặc số thập phân 1 chữ số sau dấu phẩy
                if (random.nextBoolean()) {
                    // Số thập phân với 1 chữ số sau dấu phẩy
                    double value = Math.round(random.nextDouble() * 50 * 10) / 10.0;
                    // Đảm bảo số có phần thập phân
                    if (value == Math.floor(value)) {
                        value += 0.1 * (random.nextInt(9) + 1);
                    }
                    return value;
                } else {
                    // Tạo phân số đơn giản a/b (đảm bảo không phải hỗn số)
                    int denominator = random.nextInt(9) + 2; // 2-10
                    int numerator = random.nextInt(denominator - 1) + 1; // 1-(denominator-1)
                    // Đơn giản hóa phân số
                    int gcd = gcd(numerator, denominator);
                    numerator /= gcd;
                    denominator /= gcd;
                    return (double) numerator / denominator;
                }

            case 3: // Khó: Số thập phân 2 chữ số sau dấu phẩy hoặc hỗn số
                if (random.nextBoolean()) {
                    // Số thập phân với 2 chữ số sau dấu phẩy
                    double value = Math.round(random.nextDouble() * 100 * 100) / 100.0;
                    // Đảm bảo có 2 chữ số sau dấu phẩy
                    if (value == Math.floor(value * 10) / 10) {
                        value += 0.01 * (random.nextInt(9) + 1);
                    }
                    return value;
                } else {
                    // Tạo hỗn số (số nguyên + phân số đơn giản)
                    int wholePart = random.nextInt(10) + 1; // 1-10
                    int denominator = random.nextInt(9) + 2; // 2-10
                    int numerator = random.nextInt(denominator - 1) + 1; // 1-(denominator-1)
                    // Đơn giản hóa phân số
                    int gcd = gcd(numerator, denominator);
                    numerator /= gcd;
                    denominator /= gcd;
                    // Trả về dạng: wholePart + numerator/denominator
                    return wholePart + ((double) numerator / denominator);
                }

            default:
                return random.nextInt(20) + 1;
        }
    }

    // Thuật toán Euclid tìm ước chung lớn nhất
    private int gcd(int a, int b) {
        if (b == 0) return a;
        return gcd(b, a % b);
    }

    // Sinh bộ số sử dụng thuật toán BFS
    private List<Integer> generateNumberSet(double target) {
        int setSize;
        // Số lượng số tùy theo độ khó
        switch(difficultyLevel) {
            case 1:
                setSize = random.nextInt(3) + MIN_NUMBERS; // 5-7 số
                break;
            case 2:
                setSize = random.nextInt(4) + MIN_NUMBERS; // 5-8 số
                break;
            case 3:
                setSize = random.nextInt(MAX_NUMBERS - MIN_NUMBERS + 1) + MIN_NUMBERS; // 5-10 số
                break;
            default:
                setSize = MIN_NUMBERS;
        }

        Set<Integer> numberSet = new HashSet<>();

        // Đầu tiên, tìm một cách để tạo ra target
        List<Integer> baseNumbers = findWayToTarget(target);

        // Thêm các số cơ bản vào bộ số
        numberSet.addAll(baseNumbers);

        // Thêm các số nhiễu để tạo bộ số đủ kích thước
        while (numberSet.size() < setSize) {
            // Tạo số nhiễu dựa trên độ khó
            int noise;
            switch (difficultyLevel) {
                case 1:
                    noise = random.nextInt(9) + 1; // 1-9
                    break;
                case 2:
                    noise = random.nextInt(19) + 1; // 1-19
                    break;
                case 3:
                    noise = random.nextInt(29) + 1; // 1-29
                    break;
                default:
                    noise = random.nextInt(9) + 1;
            }
            numberSet.add(noise);
        }

        // Chuyển set thành list và trộn ngẫu nhiên
        List<Integer> result = new ArrayList<>(numberSet);
        Collections.shuffle(result);

        return result;
    }

    // Tìm một cách để tạo ra target bằng BFS
    private List<Integer> findWayToTarget(double target) {
        // Sinh các số ban đầu tùy theo độ khó
        List<Integer> initialNumbers = new ArrayList<>();
        int maxInitialNumber;
        int initialCount;

        switch (difficultyLevel) {
            case 1:
                maxInitialNumber = 10; // 1-10
                initialCount = random.nextInt(2) + 2; // 2-3 số
                break;
            case 2:
                maxInitialNumber = 15; // 1-15
                initialCount = random.nextInt(2) + 2; // 2-3 số
                break;
            case 3:
                maxInitialNumber = 20; // 1-20
                initialCount = random.nextInt(3) + 2; // 2-4 số
                break;
            default:
                maxInitialNumber = 10;
                initialCount = 2;
        }

        // Tạo số ban đầu
        for (int i = 0; i < initialCount; i++) {
            initialNumbers.add(random.nextInt(maxInitialNumber) + 1);
        }

        // Kiểm tra nếu có thể đạt được target với các số ban đầu
        boolean canReachTarget = canReachTarget(initialNumbers, target);

        // Nếu không thể đạt được target, thử tìm cách khác
        int attempts = 0;
        while (!canReachTarget && attempts < 10) {
            initialNumbers.clear();
            for (int i = 0; i < initialCount; i++) {
                initialNumbers.add(random.nextInt(maxInitialNumber) + 1);
            }
            canReachTarget = canReachTarget(initialNumbers, target);
            attempts++;
        }

        // Nếu sau 10 lần thử vẫn không tìm được, tạo một cách trực tiếp
        if (!canReachTarget) {
            initialNumbers.clear();

            switch (difficultyLevel) {
                case 1: // Độ khó 1: Chỉ số tự nhiên
                    // Target đã là số tự nhiên
                    if (target <= 100) {
                        initialNumbers.add((int)target);
                        initialNumbers.add(1); // Thêm số 1 để có thể tạo ra target + 0
                    } else {
                        // Chia target thành 2 phần
                        int part1 = random.nextInt((int)target - 1) + 1;
                        int part2 = (int)target - part1;
                        initialNumbers.add(part1);
                        initialNumbers.add(part2);
                    }
                    break;

                case 2: // Độ khó 2: Phân số hoặc số thập phân 1 chữ số
                    if (target == Math.floor(target)) {
                        // Target là số nguyên
                        initialNumbers.add((int)target);
                    } else {
                        // Target có phần thập phân
                        int intPart = (int)target;
                        double fracPart = target - intPart;

                        initialNumbers.add(intPart); // Phần nguyên

                        // Xử lý phần thập phân
                        if (Math.abs(fracPart - 0.5) < 0.01) {
                            initialNumbers.add(1);
                            initialNumbers.add(2); // Để tạo 1/2
                        } else if (Math.abs(fracPart - 0.25) < 0.01) {
                            initialNumbers.add(1);
                            initialNumbers.add(4); // Để tạo 1/4
                        } else if (Math.abs(fracPart - 0.75) < 0.01) {
                            initialNumbers.add(3);
                            initialNumbers.add(4); // Để tạo 3/4
                        } else {
                            // Tạo phân số gần đúng
                            int denom = 10;
                            int num = (int)Math.round(fracPart * denom);
                            int g = gcd(num, denom);
                            initialNumbers.add(num/g);
                            initialNumbers.add(denom/g);
                        }
                    }
                    break;

                case 3: // Độ khó 3: Số thập phân 2 chữ số hoặc hỗn số
                    int intPart = (int)target;
                    double fracPart = target - intPart;

                    initialNumbers.add(intPart); // Phần nguyên

                    // Xử lý phần thập phân chi tiết hơn
                    if (fracPart > 0) {
                        if (fracPart < 0.01) {
                            initialNumbers.add(1);
                            initialNumbers.add(100); // Để tạo 1/100
                        } else {
                            int denom = 100;
                            int num = (int)Math.round(fracPart * denom);
                            int g = gcd(num, denom);
                            initialNumbers.add(num/g);
                            initialNumbers.add(denom/g);
                        }
                    }
                    break;
            }

            // Thêm một số ngẫu nhiên nữa để tăng độ phức tạp
            initialNumbers.add(random.nextInt(10) + 1);
        }

        return initialNumbers;
    }

    // Kiểm tra nếu có thể đạt được target từ một tập số
    private boolean canReachTarget(List<Integer> numbers, double target) {
        Queue<State> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        // Thêm tất cả số ban đầu vào queue
        for (Integer num : numbers) {
            List<Integer> used = new ArrayList<>();
            used.add(num);
            List<String> ops = new ArrayList<>();

            State initialState = new State(num, used, ops);
            queue.add(initialState);

            // Tạo key duy nhất cho trạng thái
            String stateKey = num + ":" + used.toString();
            visited.add(stateKey);
        }

        // BFS
        while (!queue.isEmpty()) {
            State current = queue.poll();

            // Nếu đạt được target
            if (Math.abs(current.value - target) < 0.0001) {
                return true;
            }

            // Thử áp dụng các phép tính với các số chưa dùng
            for (Integer num : numbers) {
                if (!current.numbersUsed.contains(num)) {
                    // Thêm
                    double newValue = current.value + num;
                    addNewState(queue, visited, newValue, current, num, "+");

                    // Trừ
                    newValue = current.value - num;
                    addNewState(queue, visited, newValue, current, num, "-");

                    // Nhân
                    newValue = current.value * num;
                    addNewState(queue, visited, newValue, current, num, "*");

                    // Chia (nếu không chia cho 0)
                    if (num != 0) {
                        newValue = current.value / num;
                        addNewState(queue, visited, newValue, current, num, "/");
                    }
                }
            }
        }

        return false;
    }

    private void addNewState(Queue<State> queue, Set<String> visited, double newValue,
                             State current, int num, String operation) {
        List<Integer> newUsed = new ArrayList<>(current.numbersUsed);
        newUsed.add(num);

        List<String> newOps = new ArrayList<>(current.operations);
        newOps.add(operation);

        String stateKey = newValue + ":" + newUsed.toString();
        if (!visited.contains(stateKey)) {
            State newState = new State(newValue, newUsed, newOps);
            queue.add(newState);
            visited.add(stateKey);
        }
    }

    // Thay đổi độ khó
    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
}