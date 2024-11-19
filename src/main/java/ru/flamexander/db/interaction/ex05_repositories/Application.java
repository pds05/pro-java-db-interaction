package ru.flamexander.db.interaction.ex05_repositories;

import ru.flamexander.db.interaction.ex05_repositories.for_remove.UsersDao;
import ru.flamexander.db.interaction.ex05_repositories.for_remove.UsersDaoImpl;

import java.util.Optional;

public class Application {
    // Домашнее задание:
    // - Реализовать класс DbMigrator - он должен при старте создавать все необходимые таблицы из файла init.sql
    // Доработать AbstractRepository
    // - Доделать findById(id), findAll(), update(), deleteById(id), deleteAll()
    // - Сделать возможность указывать имя столбца таблицы для конкретного поля (например, поле accountType маппить на столбец с именем account_type)
    // - Добавить проверки, если по какой-то причине невозможно проинициализировать репозиторий, необходимо бросать исключение, чтобы
    // программа завершила свою работу (в исключении надо объяснить что сломалось)
    // - Работу с полями объектов выполнять только через геттеры/сеттеры

    public static void main(String[] args) {
        DataSource dataSource = null;
        AbstractRepository<User> userAbstractRepository = null;
        AbstractRepository<Account> accountAbstractRepository = null;
        try {
            dataSource = new DataSource("jdbc:h2:file:./db;MODE=PostgreSQL");
            dataSource.connect();

            DbMigrator migrator = new DbMigrator(dataSource);
            migrator.migrate();

            userAbstractRepository = new AbstractRepository<>(dataSource, User.class);

            User user1 = new User("bob", "123", "bob");
            userAbstractRepository.create(user1);
            System.out.println("Created: " + user1);

            User user2 = new User("tom", "321", "tom");
            userAbstractRepository.create(user2);
            System.out.println("Created: " + user2);

            user2.setNickname("nik");
            userAbstractRepository.update(user2);
            System.out.println("Updated: " + user2);

            Optional<User> foundUser = userAbstractRepository.findById(user1.getId());
            System.out.println("Find by id=" + user1.getId() + ": user=" + foundUser.get());
            System.out.println("Find all:" + userAbstractRepository.findAll());
            System.out.println("Deleted - " + userAbstractRepository.deleteById(user2.getId()) + ": user=" + user2);
            System.out.println("Find all:" + userAbstractRepository.findAll());

            accountAbstractRepository = new AbstractRepository<>(dataSource, Account.class);
            Account account1 = new Account(100L, "credit", "active");
            accountAbstractRepository.create(account1);
            System.out.println("Created: " + account1);

            Account account2 = new Account(200L, "debit", "active");
            accountAbstractRepository.create(account2);
            System.out.println("Created: " + account2);

            account1.setAmount(0L);
            account1.setStatus("blocked");
            accountAbstractRepository.update(account1);
            System.out.println("Updated: " + account1);

            Optional<Account> optionalAccount = accountAbstractRepository.findById(account2.getId());
            System.out.println("Find by id=" + account2.getId() + ": account=" + optionalAccount.get());
            System.out.println("Find all:" + accountAbstractRepository.findAll());
            System.out.println("Deleted - " + accountAbstractRepository.deleteById(account2.getId()) + ": account=" + account2);
            System.out.println("Find all:" + accountAbstractRepository.findAll());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dataSource.disconnect();
            accountAbstractRepository.disconnect();
            userAbstractRepository.disconnect();
        }
    }
}
