create trigger inventory_updated_on before update on inventory
for each row execute procedure refresh_updated_on_field();
