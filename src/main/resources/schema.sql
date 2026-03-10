CREATE TABLE IF NOT EXISTS BOOKS (
    id identity,
    name varchar(50) not null,
    author varchar(50),
    genre varchar(50),
    release_date timestamp,
    reserved varchar(50)
);