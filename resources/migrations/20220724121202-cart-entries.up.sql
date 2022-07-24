create table if not exists cart_entries (
       id uuid primary key default uuid_generate_v4(),
       user_id integer references users (id) not null,
       product_id uuid references inventory (id) not null,
       amount integer not null
);
