create table if not exists users (
       id serial primary key,
       name varchar(30) unique not null,
       created_on timestamptz not null default now()
);
