create table if not exists inventory (
       id uuid primary key default uuid_generate_v4(),
       name varchar(30) unique not null,
       price decimal not null,
       amount integer not null,
       created_on timestamptz not null default now(),
       updated_on timestamptz not null default now()
);
