alter table discounts
add column expires_on timestamptz default now() + '1 day'::interval;
