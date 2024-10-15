package weshare.server;

import weshare.controller.*;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

public class Routes {
    public static final String LOGIN_PAGE = "/";
    public static final String LOGIN_ACTION = "/login.action";
    public static final String LOGOUT = "/logout";
    public static final String EXPENSES = "/expenses";
    public static final String ADD_EXPENSE = "/newexpense";
    public static final String PAYMENT_SENT = "/paymentrequests_sent";
    public static final String PAYMENT_RECEIVED = "/paymentrequests_received";
    public static final String PAYMENT_REQUEST = "/paymentrequest";
    public static final String SUBMIT_PAYMENT = "/payment.action";


    public static void configure(WeShareServer server) {
        server.routes(() -> {
            post(LOGIN_ACTION,  PersonController.login);
            get(LOGOUT,         PersonController.logout);
            get(EXPENSES,           ExpensesController.view);
            get(ADD_EXPENSE,   ExpensesController.showAddExpenseForm);
            post(EXPENSES,   ExpensesController.addExpense);
            get(PAYMENT_SENT, ExpensesController.payment_sent);
            get(PAYMENT_RECEIVED, ExpensesController.payment_received);
            get(PAYMENT_REQUEST,    ExpensesController.payment_request);
            post(PAYMENT_RECEIVED ,    ExpensesController.send_payment);
        });
    }
}
