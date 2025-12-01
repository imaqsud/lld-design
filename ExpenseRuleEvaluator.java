import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class Expense {
    private final String expenseId;
    private final String itemId;
    private final String expenseType;
    private final Double amountInUsd;
    private final String sellerType;
    private final String sellerName;

    public Expense(String expenseId, String itemId, String expenseType, double amountInUsd, String sellerType, String sellerName) {
        this.expenseId = expenseId;
        this.itemId = itemId;
        this.expenseType = expenseType;
        this.amountInUsd = amountInUsd;
        this.sellerType = sellerType;
        this.sellerName = sellerName;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public String getItemId() {
        return itemId;
    }

    public String getExpenseType() {
        return expenseType;
    }

    public Double getAmountInUsd() {
        return amountInUsd;
    }

    public String getSellerType() {
        return sellerType;
    }

    public String getSellerName() {
        return sellerName;
    }
}

interface RuleEvaluator {
    Optional<String> evaluate(List<Expense> expenses); // Returns empty if passed, else failed rule message
}

class TotalExpenseLimitRule implements RuleEvaluator {
    private final Double limit;

    public TotalExpenseLimitRule(double limit) {
        this.limit = limit;
    }

    @Override
    public Optional<String> evaluate(List<Expense> expenses) {
        double total = expenses.stream().mapToDouble(Expense::getAmountInUsd).sum();
        return total > limit ? Optional.of("Total expense exceeds " + limit) : Optional.empty();
    }
}

class SellerTypeLimitRule implements RuleEvaluator {
    private final String sellerType;
    private final double limit;

    public SellerTypeLimitRule(String sellerType, double limit) {
        this.sellerType = sellerType;
        this.limit = limit;
    }

    @Override
    public Optional<String> evaluate(List<Expense> expenses) {
        double total = expenses.stream()
                .filter(e -> sellerType.equalsIgnoreCase(e.getSellerType()))
                .mapToDouble(Expense::getAmountInUsd)
                .sum();
        return total > limit ? Optional.of("Expense for sellerType '" + sellerType + "' exceeds " + limit) : Optional.empty();
    }
}

class BlockedExpenseTypeRule implements RuleEvaluator {
    private final String blockedType;

    public BlockedExpenseTypeRule(String blockedType) {
        this.blockedType = blockedType;
    }

    @Override
    public Optional<String> evaluate(List<Expense> expenses) {
        List<Expense> violations = expenses.stream()
                .filter(e -> blockedType.equalsIgnoreCase(e.getExpenseType()))
                .toList();

        return !violations.isEmpty()
                ? Optional.of("ExpenseType '" + blockedType + "' is not allowed.")
                : Optional.empty();
    }
}

public class ExpenseRuleEvaluator {
    public static List<String> evaluateRules(List<RuleEvaluator> rules, List<Expense> expenses) {
        List<String> failedRules = new ArrayList<>();
        for (RuleEvaluator rule : rules) {
            rule.evaluate(expenses).ifPresent(failedRules::add);
        }
        return failedRules;
    }

    public static void main(String[] args) {
        List<Expense> expenses = Arrays.asList(
                new Expense("1", "Item1", "Food", 100, "restaurant", "ABC"),
                new Expense("2", "Item2", "Entertainment", 80, "club", "XYZ"),
                new Expense("3", "Item3", "Travel", 50, "taxi", "Uber")
        );

        List<RuleEvaluator> rules = Arrays.asList(
                new TotalExpenseLimitRule(175),
                new SellerTypeLimitRule("restaurant", 45),
                new BlockedExpenseTypeRule("Entertainment")
        );

        List<String> failed = evaluateRules(rules, expenses);
        System.out.println("Failed Rules:");
        failed.forEach(System.out::println);
    }
}
