create table if not exists sessions (
       id uuid primary key default uuid_generate_v4(),
       user_id integer references users (id) not null,
       started_on timestamptz not null default now(),
       expires_on timestamptz not null default now() + '1 hour'::interval
);
