create table if not exists admins (
       id uuid primary key default uuid_generate_v4(),
       user_id integer references users (id) not null,
       promoted_on timestamptz not null default now()
);
