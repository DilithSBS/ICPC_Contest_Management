import java.util.Scanner;

public class sol {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNextLong()) {
            long a = sc.nextLong();
            if (sc.hasNextLong()) {
                long b = sc.nextLong();
                System.out.println(a + b);
            }
        }
        sc.close();
    }
}