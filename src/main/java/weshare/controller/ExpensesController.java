package weshare.controller;

import io.javalin.http.Handler;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
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
import java.util.*;

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
        boolean hasUnpaidExpenses = expenses.stream()
                .flatMap(expense -> expense.listOfPaymentRequests().stream())
                .anyMatch(request -> !request.isPaid());

        // Pass expenses, totalAmount, and hasUnpaidExpenses to the view
        Map<String, Object> viewModel = Map.of(
                "expenses", expenses,
                "totalAmount", totalAmount,
                "hasUnpaidExpenses", hasUnpaidExpenses // Add the boolean to the view model
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

        // Redirect back to the expenses page or render a success message
        context.render("/expenses.html", viewModel); // Redirect to the expenses list page
    };

    public static final Handler payment_request = context -> {
        String expenseId = context.queryParam("expenseId");

        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Optional<Expense> expenseOptional = expensesDAO.get(UUID.fromString(expenseId));// Assuming you have this method

        Expense expense = expenseOptional.get();
        Collection<PaymentRequest> payment_requests = expense.listOfPaymentRequests();

        Map<String, Object> viewModel = Map.of(
                "expense", expense,
                "requests", payment_requests

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

        MonetaryAmount totalAmount = payment_received.stream()
                .map(PaymentRequest::getAmountToPay)
                .reduce(MonetaryAmount::add) // Sum up the amounts
                .orElse(Monetary.getDefaultAmountFactory().setCurrency("ZAR").setNumber(0).create());

        Map<String, Object> viewModel = Map.of(
                "payments", payment_received,
                "totalAmount", totalAmount
        );

        context.render("/paymentrequests_received.html", viewModel);
    };


    public static final Handler payment_sent = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<PaymentRequest> payment_sent = expensesDAO.findPaymentRequestsSent(personLoggedIn);

        MonetaryAmount totalAmount = payment_sent.stream()
                .map(PaymentRequest::getAmountToPay)
                .reduce(MonetaryAmount::add) // Sum up the amounts
                .orElse(Monetary.getDefaultAmountFactory().setCurrency("ZAR").setNumber(0).create());

        Map<String, Object> viewModel = Map.of(
                "payments", payment_sent,
                "totalAmount", totalAmount
        );

        context.render("/paymentrequests_sent.html", viewModel);
    };

    public static Handler send_payment = context -> {
        // Access the DAO (Data Access Object) to interact with expenses
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);

        // Retrieve the expense ID from the query parameters and parse it into a UUID
        UUID expenseId = UUID.fromString(context.queryParam("expenseId"));

        // Fetch the corresponding expense object from the database using the expense ID
        Expense expense = expensesDAO.get(expenseId).get();

        // Calculate the total payment request by subtracting the requested amount from the total amount
        long totalPaymentRequest = expense.getAmount().getNumber().intValueExact() -
                expense.totalAmountOfPaymentsRequested().getNumber().intValueExact();

        // Create a view model to pass the data to the HTML template
        HashMap<String, Object> viewModel = new HashMap<>();
        viewModel.put("expense", expense);
        viewModel.put("payments", expense.listOfPaymentRequests());
        viewModel.put("total_paymentrequest", MoneyHelper.amountOf(totalPaymentRequest));

        // Render the template and pass the view model data to it
        context.render("paymentrequest.html", viewModel);
    };

}
