create table if not exists applied_discounts (
       id uuid primary key default uuid_generate_v4(),
       user_id integer references users (id) unique not null,
       discount_id uuid references discounts (id) not null
);
