create or replace function refresh_updated_on_field()
returns trigger as $$
begin
        new.updated_on = now();
        return new;
end;
$$ language 'plpgsql';
