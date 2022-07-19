# Shopping Cart API

This project implements a simple shopping cart API which offers the
following actions:

- create new user
- log user in
- log user out
- promote user to admin
- add one item to logged in user's shopping cart
- remove one item to logged in user's shopping cart
- clean logged in user's shopping cart
- generate totals and subtotals of a logged in user's shopping cart
- get logged in user's shopping cart content as JSON
- register discount coupon
- apply a discount coupon to logged in user's shopping cart
- register a product as part of inventory
- remove a product from inventory
- change product's price
- increase product available amount in inventory
- decrease product available amount in inventory

## API documentation

All of this API's endpoints start with the `/api/` prefix.
Here we document those in detail, explaining the expected
behavior of each of them

### User endpoints

In order to create a new user, it is utilized an HTTP request such as
the following:

    POST /api/user/create
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "Luiz"
    }

In case the user name is not in use, a new user with that name is
created, which is signaled by the `201` response code.
In case the user name is already in use, no user is created, which is
signaled by the `409` response code.

In order to log a user in, it is utilized an HTTP request like the one which
follows:

    POST /api/user/login
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "Luiz"
    }

Due to the simplistic nature of this project, no passwords are
requested.
In case the user name exists, such user will be considered logged in
until an appropriate request to `POST /api/user/logout` is
performed. A successful login is signaled by the `200` response code,
along with a body containing the just created session token. Also, any
shopping cart data stored in the database will be recovered.
In case the user name does not exist, no login is performed and such
failed login attempt is indicated by the `404` response code.
When an already logged in user tries to log in a second time, their
request is replied with the `400` response code.

In order to log a user out, it is utilized an HTTP request similar to
the following one:

    POST /api/user/logout
    Content-Type: application/json; charset=utf-8
    
    {
        "session": <session-uuid>
    }

In case the referred session is active, its user will be considered logged out
until an appropriate request to `POST /api/user/login` is performed.
A successful logout is signaled by the `200` response code. Also, any
shopping cart data will be stored into the database.
In case the referred session does not exist, no logunt is performed and such
failed logout attempt is indicated by the `404` response code.
When an already finished session is requested to be finished a second
time, their request is replied with the `400` response code.

In order to promote a user to the admin role, an HTTP request like the
following one should be used:

    POST /api/admin
    Content-Type: application/json; charset=utf-8
    
    {
        "user": "userA",
        "session": <session-uuid>
    }

Initially, the system would have no admins and the first user which
promotes themselves to admin will be considered the first system
admin. After that, users will not be able to promote themselves to admin.
In case the session's user is in fact an admin and
the user being promoted is not, then its promotion is successful,
which is signaled by the `200` response code.
In case the referred session does not belong to an admin user, a `403`
response code will be returned.
In case the `user` field does not correspond to an existing user, a
response whose code is `404` will be returned.

### Shopping cart endpoints

Here it is assumed that a user has exactly one shopping cart at all times.

In order to add one item to a logged in user's shopping cart, an HTTP
request like this one should be used:

    PUT /api/cart/add
    Content-Type: application/json; charset=utf-8
    
    {
        "product": "carrot",
        "amount": 2,
        "session": <session-uuid>
    }

If adding such product in such amount is in accordance with the
product's amount in inventory, such product addition is successful,
which is signaled by the `200` response code.
If the referred session has been finished, a `401` reponse code is returned.
If there is no such a product, a response whose code is `404` is returned.
If there are not enough product items in inventory to satisfy the
request, a response whose code is `409` returned in conjunction with a
body detailing how many product items are available in inventory.
Any request regarding a non-positive amount is a bad request, and
therefore it is replied with the `400` response code.

In order to remove one item to a logged in user's shopping cart, an HTTP
request like this one should be used:

    PUT /api/cart/remove
    Content-Type: application/json; charset=utf-8
    
    {
        "product": "carrot",
        "amount": 2,
        "session": <session-uuid>
    }

If removing such product in such amount is in accordance with the
product's current amount in the shopping cart, such product removal is
successful, which is signaled by the `200` response code.
If the referred session has been finished, a `401` reponse code is returned.
If there is no such a product, a response whose code is `404` is returned.
Any request regarding a non-positive amount is a bad request, and
therefore it is replied with the `400` response code.

In order to clean the entire content of a logged in user's shopping
cart, an HTTP request as follows should be used:

    DELETE /api/cart
    Content-Type: application/json; charset=utf-8
    
    {
        "session": <session-uuid>
    }

If the referred session has been finished, a `401` reponse code is returned.

In order to generate the subtotal (before any discount) and total
(after any discount) of a shopping cart, an HTTP request as follows
should be used:

    GET /api/cart/totals
    Content-Type: application/json; charset=utf-8
    
    {
        "session": <session-uuid>
    }

If the referred session is active, then a response whose code is `200`
is returned along with a body containing the shopping cart's total and
subtotal values.
If the referred session is not active, then a `401` response code will
indicate that.

In order to retrieve a JSON representation of a shopping cart, an HTTP
as follows should be used:

    GET /api/cart
    Content-Type: application/json; charset=utf-8
    
    {
        "session": <session-uuid>
    }

If the referred session is active, then a response whose code is `200`
is returned as well as a body containing JSON representing the
contents of the shopping cart associated with the session.
If the referred session is not active, that will be signed by a `401`
response code.

### Coupon endpoints

In order to register a new discount coupon, an HTTP request as follows
should be used:

    POST /api/discounts
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "coupon-a",
        "amount": 1000,
        "discount": 0.10,
        "session": <session-uuid>
    }

If the coupon name is not in use and the referred session is
associated with an admin user, then the new discount coupon is
registered successfully, which is signaled by a `201` response code.
If the referred session does not belong to an admin user, a `403`
response code indicates that. 
If the coupon name is already in use, a `409` response code signals that.
Any request regarding a non-positive amount is a bad request, and
therefore it is replied with the `400` response code.
Any request regarding a discount not between `0.01` and `1.00` is a
bad request, and therefore it is replied with the `400` reponse code.

In order to apply a discount coupon to some user's shopping cart, an
HTTP request like this should be used:

    PUT /api/discounts
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "coupon-a",
        "session": <session-uuid>
    }

If the coupom name is valid as well as the referred session, then the
discount coupon is applied to that user's shopping cart.
If the coupom name does not correspond to an existing coupon's name,
a `404` response code will indicate that.
If the referred session has been finished, a `401` response code will
indicate that.

### Inventory endpoints

In order to register a product as part of inventory, an HTTP request
like this should be used:

    POST /api/inventory
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "carrot",
        "price": 3.99,
        "amount": 20,
        "session": <session-uuid>
    }

If there is no product in inventory with such name and the referred
session is from an admin user, then the product is registered in
inventory, which is signaled by a `201` response code.
If the product name is already in use in inventory, that is signaled
by a `409` response code.
If the referred session is not from an admin user, that is signaled by
a `403` error code.
If the referred session has been finished, a `401` response code will
indicate that.

In order to delete a product from inventory, an HTTP request like this
should be used:

    DELETE /api/inventory
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "carrot",
        "session": <session-uuid>
    }

If the product name is indeed registered in inventory and the referred
session is from an admin user, then the product entry is removed from
inventory, which is signaled by a `200` response code.
If the product name is not present in inventory, that is signaled by a
`404` error code.
If the referred session is not from an admin user, that is signaled by
a `403` error code.
If the referred session has been finished, a `401` response code will
indicate that.

In order to change a product's price, an HTTP request like this should
be used:

    PUT /api/inventory/price
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "carrot",
        "price": 4.99,
        "session": <session-uuid>
    }

If the product name is indeed registered in inventory and the referred
session is from an admin user, then the product entry has its price
updated, which will be signaled by a `200` response code.
If the product name is not present in inventory, that is signaled by a
`404` error code.
If the referred session is not from an admin user, that is signaled by
a `403` error code.
If the referred session has been finished, a `401` response code will
indicate that.

In order to increase the amount of items of a certain product in
inventory, an HTTP request like this should be used:

    PUT /api/inventory/increase
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "carrot",
        "amountToIncrease": 1000,
        "session": <session-id>
    }

If the product name is indeed registered in inventory and the referred
session is from an admin user, then the product entry has its item
amount increased, which will be signaled by a `200` response code.
If the product name is not present in inventory, that is signaled by a
`404` error code.
If the referred session is not from an admin user, that is signaled by
a `403` error code.
If the referred session has been finished, a `401` response code will
indicate that.

In order to decrease the amount of items of a certain product in
inventory, an HTTP request like this should be used:

    PUT /api/inventory/decrease
    Content-Type: application/json; charset=utf-8
    
    {
        "name": "carrot",
        "amountToDecrease": 1000,
        "session": <session-id>
    }

If the product name is indeed registered in inventory and the referred
session is from an admin user, then the product entry has its item
amount decreased, which will be signaled by a `200` response code.
If the product name is not present in inventory, that is signaled by a
`404` error code.
If the referred session is not from an admin user, that is signaled by
a `403` error code.
If the referred session has been finished, a `401` response code will
indicate that.
