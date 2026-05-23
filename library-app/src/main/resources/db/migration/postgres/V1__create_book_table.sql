CREATE SCHEMA LIBRARY

CREATE TABLE IF NOT EXISTS LIBRARY.BOOKS (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(50) not null,
    author varchar(50),
    genre varchar(50),
    release_date date,
    reserved varchar(50)
);