package ru.flamexander.db.interaction.ex05_repositories;

@RepositoryTable(title = "accounts")
public class Account {
    @RepositoryIdField
    @RepositoryField
    private Long id;

    @RepositoryField
    private Long amount;

    @RepositoryField
    private String accountType;

    @RepositoryField
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Account() {
    }

    public Account(Long amount, String accountType, String status) {
        this.amount = amount;
        this.accountType = accountType;
        this.status = status;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", amount=" + amount +
                ", accountType='" + accountType + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
