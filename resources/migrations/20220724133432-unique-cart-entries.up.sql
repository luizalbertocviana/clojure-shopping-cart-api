alter table cart_entries
add constraint unique_cart_entries unique (user_id, product_id);
