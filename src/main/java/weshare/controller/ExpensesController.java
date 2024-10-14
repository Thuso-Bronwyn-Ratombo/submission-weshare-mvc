package weshare.controller;

import io.javalin.http.Handler;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.jetbrains.annotations.NotNull;
import weshare.model.Expense;
import weshare.model.MoneyHelper;
import weshare.model.PaymentRequest;
import weshare.model.Person;
import weshare.persistence.ExpenseDAO;
import weshare.server.ServiceRegistry;
import weshare.server.WeShareServer;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static weshare.model.MoneyHelper.ZERO_RANDS;

public class ExpensesController {

    public static final Handler view = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<Expense> expenses = expensesDAO.findExpensesForPerson(personLoggedIn);
        MonetaryAmount totalAmount = expenses.stream()
                .map(Expense::getAmount) // Get the MonetaryAmount
                .reduce(MonetaryFunctions.sum()) // Sum the amounts
                .orElse(Money.zero(MoneyHelper.ZERO_RANDS.getCurrency()));

        // Pass expenses and totalAmount to the view
        Map<String, Object> viewModel = Map.of(
                "expenses", expenses,
                "totalAmount", totalAmount
        );

        context.render("expenses.html", viewModel);
    };

    // Method to show the form for adding a new expense
    public static final Handler showAddExpenseForm = context -> {
        // Render the add expense form view
        context.render("newexpense.html"); // Create a corresponding Thymeleaf template
    };

    // Method to handle the form submission for adding a new expense
    public static final Handler addExpense = context -> {
        // Extract parameters from the form
        String description = context.formParam("description"); // Assuming a field named "description"
        String amountStr = context.formParam("amount"); // Assuming a field named "amount"
        String dateStr = context.formParam("date"); // Assuming a field named "date"

        // Validate and process the amount; you may need additional validation
        MonetaryAmount amount = parseAmount(amountStr);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        // Parse the date input from the form (assuming the format is "dd/MM/yyyy")
        LocalDate date;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            date = LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            // Handle the case where the date is invalid, possibly by redirecting or showing an error
            context.render("/exception.html");
            return;
        }

        // Create a new Expense object
        Expense newExpense = new Expense(personLoggedIn, description, amount, date);

        // Save the expense using the DAO
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        expensesDAO.save(newExpense);

        // Redirect back to the expenses page or render a success message
        context.redirect("/expenses"); // Redirect to the expenses list page
    };

    public static final Handler payment_request = context -> {
        String expenseId = context.queryParam("expenseId");

        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Optional<Expense> expense = expensesDAO.get(UUID.fromString(expenseId));// Assuming you have this method

        Map<String, Object> viewModel = Map.of(
                "expense", expense
        );

        context.render("paymentrequest.html", viewModel);
    };

    // Helper method to parse the amount from String to MonetaryAmount
    private static MonetaryAmount parseAmount(String amountStr) {
        // Implement parsing logic, e.g., using Monetary.getDefaultCurrency() to create the MonetaryAmount
        // For example:
        try {
            // Convert string to monetary amount, handle exceptions as needed
            return Monetary.getDefaultAmountFactory().setCurrency("ZAR").setNumber(Double.parseDouble(amountStr)).create();
        } catch (NumberFormatException e) {
            // Handle parsing error
            throw new RuntimeException("Invalid amount format: " + amountStr);
        }
    }

    public static final Handler payment_received = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<PaymentRequest> payment_received = expensesDAO.findPaymentRequestsReceived(personLoggedIn);

        Map<String, Object> viewModel = Map.of(
                "payments", payment_received
        );

        context.render("/paymentrequests_received.html", viewModel);
    };


    public static final Handler payment_sent = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<PaymentRequest> payment_sent = expensesDAO.findPaymentRequestsSent(personLoggedIn);

        Map<String, Object> viewModel = Map.of(
                "payments", payment_sent
        );

        context.render("/paymentrequests_sent.html", viewModel);
    };
}
