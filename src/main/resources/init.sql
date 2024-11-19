create table if not exists users
(
    f_id       bigserial primary key,
    f_login    varchar(255),
    f_password varchar(255),
    f_nickname varchar(255)
);
create table if not exists accounts
(
    id          bigserial primary key,
    amount      bigint,
    tp          varchar(255),
    status      varchar(255),
    accountType varchar(255)
);
create table users
(
    id       bigserial primary key,
    login    varchar(255),
    password varchar(255),
    nickname varchar(255)
);
