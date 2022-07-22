create table if not exists discounts (
       id uuid primary key default uuid_generate_v4(),
       name varchar(30) unique not null,
       amount integer not null,
       discount decimal not null
);
