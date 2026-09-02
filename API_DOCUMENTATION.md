# AnushaBazaar Customer API Documentation

**Base URL:** `https://api.anushatechnologies.com`  
**Auth:** Bearer JWT token in `Authorization: Bearer <token>` header  
**JWT Expiry:** 15 minutes (use `/api/auth/refresh` to renew)  
**Refresh Token Expiry:** 30 days  

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Customer Profile](#2-customer-profile)
3. [Categories](#3-categories)
4. [Sub-Categories](#4-sub-categories)
5. [Products](#5-products)
6. [Cart](#6-cart)
7. [Address](#7-address)
8. [Checkout Settings](#8-checkout-settings)
9. [Coupons](#9-coupons)
10. [Orders](#10-orders)
11. [Payment (Razorpay)](#11-payment-razorpay)
12. [Wallet](#12-wallet)
13. [Notifications](#13-notifications)
14. [Wishlist](#14-wishlist)
15. [Ratings](#15-ratings)
16. [Tracking](#16-tracking)
17. [Banners](#17-banners)
18. [Marquee](#18-marquee)
19. [Policies](#19-policies)
20. [App Settings & Version](#20-app-settings--version)

---

## 1. Authentication

All auth endpoints are **public** (no JWT required).

### 1.1 Check Phone Exists

Check if a phone number is already registered. Call this BEFORE sending Firebase OTP to decide login vs signup screen.

```
GET /api/auth/check-phone/{phoneNumber}
```

**Path param:** `phoneNumber` — include country code e.g. `+919876543210`

**Response 200:**
```json
{ "exists": true }
```

---

### 1.2 Signup (New User)

```
POST /api/auth/signup
Content-Type: application/json
```

**Request body:**
```json
{
  "firebaseIdToken": "eyJhbGci...",   // Firebase ID token after phone OTP verified
  "name": "Ravi Kumar",               // required
  "email": "ravi@example.com",        // optional
  "fcmToken": "fCM_device_token"      // optional — for push notifications
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGci...",
  "jwtToken": "eyJhbGci...",
  "refreshToken": "uuid-refresh-token",
  "expiresIn": 900,
  "customerId": 12,
  "phoneNumber": "+919876543210",
  "name": "Ravi Kumar",
  "email": "ravi@example.com",
  "roles": "CUSTOMER"
}
```

**Error 409** — account already exists:
```json
{ "error": "Account already exists. Please login." }
```

**Error 401** — bad Firebase token:
```json
{ "error": "Invalid or expired Firebase token" }
```

---

### 1.3 Login (Existing User)

```
POST /api/auth/login
Content-Type: application/json
```

**Request body:**
```json
{
  "firebaseIdToken": "eyJhbGci...",
  "fcmToken": "fCM_device_token"   // optional
}
```

**Response 200:** Same shape as signup response.

**Error 404** — phone not registered:
```json
{ "error": "No account found. Please sign up as a new user." }
```

---

### 1.4 Refresh Access Token

Call when JWT expires (every 15 min).

```
POST /api/auth/refresh
Content-Type: application/json
```

**Request body:**
```json
{ "refreshToken": "uuid-refresh-token" }
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "new-uuid-refresh-token",
  "expiresIn": 900
}
```

**Error 401** — refresh token expired/revoked:
```json
{ "error": "Refresh token expired" }
```

---

### 1.5 Logout

```
POST /api/auth/logout
Content-Type: application/json
```

**Request body:**
```json
{ "refreshToken": "uuid-refresh-token" }
```

**Response 200:**
```json
{ "message": "Logged out successfully" }
```

---

## 2. Customer Profile

Requires `Authorization: Bearer <token>`.

### 2.1 Get Profile

```
GET /api/customer/profile
Authorization: Bearer <token>
```

**Response 200:**
```json
{
  "id": 12,
  "name": "Ravi Kumar",
  "phoneNumber": "+919876543210",
  "email": "ravi@example.com",
  "isActive": true,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-03-20T14:00:00"
}
```

---

### 2.2 Update Profile

```
PUT /api/customer/profile
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "name": "Ravi K",         // optional
  "email": "new@email.com"  // optional
}
```

**Response 200:** Same shape as Get Profile.

---

## 3. Categories

### 3.1 Get All Categories (Public)

```
GET /api/categories
```

**Response 200:**
```json
[
  {
    "id": 1,
    "name": "Fruits & Vegetables",
    "imageUrl": "https://s3.amazonaws.com/...",
    "displayOrder": 1,
    "isActive": true,
    "discount": 0.0
  },
  {
    "id": 2,
    "name": "Dairy & Eggs",
    "imageUrl": "https://s3.amazonaws.com/...",
    "displayOrder": 2,
    "isActive": true,
    "discount": 5.0
  }
]
```

---

### 3.2 Get Category by ID (Public)

```
GET /api/categories/{id}
```

---

### 3.3 Search Categories (Public)

```
GET /api/categories/search?keyword=fruit
```

---

## 4. Sub-Categories

### 4.1 Get All Sub-Categories (Public)

```
GET /api/subcategories
```

**Response 200:**
```json
[
  {
    "id": 10,
    "name": "Leafy Greens",
    "categoryId": 1,
    "categoryName": "Fruits & Vegetables",
    "imageUrl": "https://s3.amazonaws.com/...",
    "displayOrder": 1,
    "isActive": true
  }
]
```

---

### 4.2 Get Sub-Categories by Category (Public)

```
GET /api/subcategories/{categoryId}
```

Returns all sub-categories belonging to `categoryId`.

---

### 4.3 Get Sub-Category by ID (Public)

```
GET /api/subcategories/detail/{id}
```

---

## 5. Products

### 5.1 Get All Products (Public)

```
GET /api/products
GET /api/products?storeId=1
```

**Response 200:**
```json
[
  {
    "id": 101,
    "name": "Amul Butter 500g",
    "description": "Fresh dairy butter",
    "isActive": true,
    "isTrending": false,
    "bestSeller": true,
    "displayOrder": 1,
    "imageUrl": "https://s3.amazonaws.com/.../butter.jpg",
    "videoUrl": null,
    "categoryId": 2,
    "categoryName": "Dairy & Eggs",
    "subCategoryId": 20,
    "subCategoryName": "Butter",
    "storeId": 1,
    "storeName": "Main Store",
    "minPrice": 55.0,
    "maxPrice": 55.0,
    "variants": [
      {
        "id": 201,
        "name": "500g",
        "sku": "AMB-500",
        "price": 55.0,
        "discountPrice": null,
        "stock": 100,
        "isActive": true,
        "displayOrder": 1
      }
    ],
    "images": []
  }
]
```

---

### 5.2 Get Product by ID (Public)

```
GET /api/products/{id}
```

---

### 5.3 Search Products (Public)

Basic keyword search on product name.

```
GET /api/products/search?keyword=butter
```

---

### 5.4 Instant Search Suggestions (Public)

Zepto-style fast suggestions — returns top N results ranked by relevance (name starts-with first, then contains, then category match).

```
GET /api/products/suggestions?q=amul&limit=10
```

**Query params:**
- `q` — search keyword (min 1 character)
- `limit` — max results (default 10, max 20)

**Response 200:** Array of product objects (same shape as 5.1).

---

### 5.5 Full Paginated Search (Public)

```
GET /api/products/search/paginated?q=amul&page=0&size=20
```

**Response 200:**
```json
{
  "content": [ /* product objects */ ],
  "totalElements": 45,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

### 5.6 Trending Products (Public)

```
GET /api/products/trending
```

Returns products where `isTrending = true` and `isActive = true`.

---

### 5.7 Best Seller Products (Public)

```
GET /api/products/bestseller
```

Returns products where `bestSeller = true` and `isActive = true`.

---

### 5.8 Filter Products (Public)

```
GET /api/products/filter?categoryId=2&subCategoryId=20&minPrice=10&maxPrice=100&trending=false&keyword=butter
```

**All params optional.** When provided, results are ANDed together.

---

## 6. Cart

Requires `Authorization: Bearer <token>`.

### 6.1 Get Cart

```
GET /api/cart
Authorization: Bearer <token>
```

**Response 200:**
```json
{
  "cartId": 7,
  "items": [
    {
      "id": 55,
      "variantId": 201,
      "variantName": "500g",
      "productId": 101,
      "productName": "Amul Butter 500g",
      "productImage": "https://s3.amazonaws.com/...",
      "quantity": 2,
      "unitPrice": 55.00,
      "totalPrice": 110.00
    }
  ],
  "subtotal": 110.00,
  "deliveryCharge": 20.00,
  "estimatedDeliveryCharge": 20.00,
  "platformFee": 2.00,
  "grandTotal": 132.00
}
```

> **Note:** `deliveryCharge` and `platformFee` are 0 when the cart is empty.

---

### 6.2 Add Item to Cart

```
POST /api/cart/items
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "variantId": 201,
  "quantity": 2
}
```

**Response 200:** Full cart response (same as 6.1).

---

### 6.3 Update Item Quantity

```
PUT /api/cart/items/{itemId}?quantity=3
Authorization: Bearer <token>
```

**Path param:** `itemId` — cart item ID (from items[].id)  
**Query param:** `quantity` — new quantity (send 0 to remove)

**Response 200:** Full cart response.

---

### 6.4 Remove Item from Cart

```
DELETE /api/cart/items/{itemId}
Authorization: Bearer <token>
```

**Response 200:** Full cart response.

---

### 6.5 Clear Cart

```
DELETE /api/cart
Authorization: Bearer <token>
```

**Response 200:** Empty (HTTP 200)

---

### 6.6 Merge Guest Cart (on login)

Use this after login to merge locally stored guest cart items into the server cart.

```
POST /api/cart/merge
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
[
  { "variantId": 201, "quantity": 2 },
  { "variantId": 305, "quantity": 1 }
]
```

**Response 200:** Full cart response.

---

## 7. Address

Requires `Authorization: Bearer <token>`.

### 7.1 Get All Addresses

```
GET /api/addresses
Authorization: Bearer <token>
```

**Response 200:**
```json
[
  {
    "id": 3,
    "addressType": "HOME",
    "flatNumber": "Flat 401",
    "addressLine1": "Sunshine Apartments",
    "addressLine2": "MG Road",
    "landmark": "Near Big Bazaar",
    "city": "Hyderabad",
    "state": "Telangana",
    "postalCode": "500001",
    "latitude": 17.385,
    "longitude": 78.4867,
    "isDefault": true
  }
]
```

---

### 7.2 Add Address

```
POST /api/addresses
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "addressType": "HOME",                      // HOME | WORK | OTHER
  "flatNumber": "Flat 401",                   // optional
  "addressLine1": "Sunshine Apartments",      // REQUIRED
  "addressLine2": "MG Road",                  // optional
  "landmark": "Near Big Bazaar",              // optional
  "city": "Hyderabad",                        // REQUIRED
  "state": "Telangana",                       // optional
  "postalCode": "500001",                     // REQUIRED
  "latitude": 17.385,                         // MANDATORY — get from device GPS
  "longitude": 78.4867,                       // MANDATORY — get from device GPS
  "isDefault": true
}
```

> **latitude and longitude are MANDATORY.** Get these from device GPS before saving.
> Missing lat/lng returns HTTP 400 with field-level errors (see Validation Error below).
> If `isDefault: true`, any previously default address is cleared.
> If `isDefault` is omitted, backend treats it as `false`.

**Response 200:** Address object (same shape as 7.1 list item).

**Validation Error 400** (when lat/lng or required fields are missing):
```json
{
  "status": 400,
  "error": "Validation Failed",
  "fields": {
    "latitude": "latitude is required — get from device GPS",
    "longitude": "longitude is required — get from device GPS",
    "addressLine1": "addressLine1 is required",
    "city": "city is required",
    "postalCode": "postalCode is required"
  }
}
```

---

### 7.3 Update Address

```
PUT /api/addresses/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:** Same as 7.2 — latitude and longitude are MANDATORY here too.
This is still a full update endpoint, so resend the full address payload.
**Response 200:** Updated address object.
**Error 400:** Validation failed (lat/lng missing — same error shape as 7.2).
**Error 403:** If address doesn't belong to authenticated customer.
**Error 404:** If address ID does not exist.

---

### 7.4 Delete Address

```
DELETE /api/addresses/{id}
Authorization: Bearer <token>
```

**Response 200:** Empty  
**Error 403:** If address doesn't belong to authenticated customer.
**Error 404:** If address ID does not exist.

---

## 8. Checkout Settings

### 8.1 Get Checkout Settings (Public)

```
GET /api/checkout-settings
```

**Response 200:**
```json
{
  "success": true,
  "settings": {
    "deliveryCharge": 20.00,
    "platformFee": 2.00,
    "onlinePaymentEnabled": true,
    "cashOnDeliveryEnabled": true
  }
}
```

---

## 9. Coupons

### 9.1 Get Active Coupons (Public)

```
GET /api/coupons/active
```

**Response 200:**
```json
[
  {
    "id": 1,
    "code": "WELCOME10",
    "discountType": "PERCENTAGE",  // PERCENTAGE | FLAT
    "discountValue": 10.0,
    "minCartValue": 200.0,
    "maxDiscount": 50.0,           // max discount cap (for PERCENTAGE type)
    "expiryDate": "2025-12-31",
    "isActive": true
  }
]
```

---

### 9.2 Apply / Validate Coupon

```
GET /api/coupons/apply?code=WELCOME10&customerId=12&cartValue=500
```

**Query params:**
- `code` — coupon code
- `customerId` — customer ID (from login response)
- `cartValue` — cart subtotal (before delivery charge)

**Response 200:**
```json
{
  "success": true,
  "code": "WELCOME10",
  "discount": 50.00,
  "finalValue": 450.00
}
```

**Error 400:**
```json
{ "success": false, "error": "Coupon minimum cart value is ₹200" }
```

---

## 10. Orders

Requires `Authorization: Bearer <token>`.

### 10.1 Place Order

```
POST /api/orders
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: <uuid>   // recommended to prevent duplicate orders on retry/double-tap
```

**Request body:**
```json
{
  "addressId": 3,
  "paymentMethod": "COD",   // COD | ONLINE | WALLET
  "couponCode": "WELCOME10" // optional
}
```

> The backend reads the cart for this customer, validates it, calculates totals, saves the order, clears the cart, and notifies the store via Firebase.
> Unknown JSON fields are rejected with HTTP 400.
> If the same `Idempotency-Key` is reused for the same customer, the backend returns the already-created order instead of creating a duplicate.

**Response 200:**
```json
{
  "id": 88,
  "orderId": 88,
  "orderNumber": "GRO-20240324-1001",
  "subtotal": 450.00,
  "deliveryCharge": 20.00,
  "platformFee": 2.00,
  "handlingCharge": 0.00,
  "smallCartFee": 0.00,
  "walletApplied": 0.00,
  "paidAmount": 0.00,
  "tax": 0.00,
  "discount": 50.00,
  "grandTotal": 422.00,
  "paymentMethod": "COD",
  "orderStatus": "PLACED",
  "paymentStatus": "PENDING",
  "placedAt": "2024-03-24T11:00:00",
  "createdAt": "2024-03-24T11:00:00",
  "address": {
    "id": 3,
    "addressType": "HOME",
    "flatNumber": "Flat 401",
    "addressLine1": "Sunshine Apartments",
    "city": "Hyderabad",
    "state": "Telangana",
    "postalCode": "500001"
  },
  "items": [
    {
      "id": 1001,
      "productId": 101,
      "variantId": 201,
      "productName": "Amul Butter 500g",
      "variantName": "500g",
      "sku": "AMB-500",
      "quantity": 2,
      "unitPrice": 55.00,
      "totalPrice": 110.00,
      "imageUrl": "https://s3.amazonaws.com/...",
      "storeName": "Main Store"
    }
  ],
  "storeGroups": null,
  "deliveryPersonName": null,
  "deliveryPersonPhone": null,
  "estimatedDeliveryTime": null
}
```

**Error 400:**
```json
{ "error": "Cart is empty" }
```

**Payment Method Values:**
- `COD` — Cash On Delivery (payment collected at doorstep)
- `ONLINE` — Online payment via Razorpay (call `/api/payment/initiate` after placing order)
- `WALLET` — Deduct full payable amount from wallet during order placement

---

### 10.2 Get My Orders

```
GET /api/orders
Authorization: Bearer <token>
```

**Response 200:** Array of order objects (same shape as 10.1).

---

### 10.3 Get Order by ID

```
GET /api/orders/{orderId}
Authorization: Bearer <token>
```

**Response 200:** Single order object.  
**Error 403:** If order doesn't belong to authenticated customer.

---

### 10.4 Cancel Order

```
PATCH /api/orders/{orderId}/cancel
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body (optional):**
```json
{ "reason": "Changed my mind" }
```

**Response 200:** Updated order object with `orderStatus: "CANCELLED"`.

> Cancellation is only allowed while the order is in early statuses (PLACED, PENDING). Once a rider picks up the order, cancellation is blocked by the service.

---

### 10.5 Get Recently Ordered Products

Returns distinct products from the customer's last few orders (useful for "Buy Again" section).

```
GET /api/orders/recent-products
Authorization: Bearer <token>
```

**Response 200:** Array of product objects (same shape as 5.1).

---

## 11. Payment (Razorpay)

Requires `Authorization: Bearer <token>`.

### Online Payment Flow:

1. Place order with `paymentMethod: "ONLINE"` → get `orderId`
2. Call `/api/payment/initiate` → get Razorpay order details
3. Open Razorpay SDK checkout in the app
4. After user pays, Razorpay calls your success handler with `razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature`
5. Call `/api/payment/verify` → backend verifies HMAC and marks order PAID

> For wallet orders, do NOT call payment APIs. The order is paid during `/api/orders`.

---

### 11.1 Initiate Payment

```
POST /api/payment/initiate
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{ "orderId": 88 }
```

**Response 200:**
```json
{
  "razorpayOrderId": "order_XXXXXXXXXX",
  "amount": 42200,          // in paise (422.00 × 100)
  "currency": "INR",
  "receipt": "GRO-20240324-1001",
  "keyId": "rzp_live_XXXXXXXX"
}
```

---

### 11.2 Verify Payment

```
POST /api/payment/verify
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "razorpayOrderId": "order_XXXXXXXXXX",
  "razorpayPaymentId": "pay_XXXXXXXXXX",
  "razorpaySignature": "HMAC_SHA256_signature",
  "receipt": "GRO-20240324-1001"
}
```

**Response 200:**
```json
{ "success": true, "message": "Payment verified successfully" }
```

**Error 400:**
```json
{ "success": false, "error": "Invalid payment signature" }
```

---

### 11.3 Request Refund

```
POST /api/payment/refund/request
Authorization: Bearer <token>
Content-Type: application/json
```

Customer endpoint. The order must belong to the authenticated customer and must already be cancelled/rejected.

```json
{
  "orderId": 3001,
  "reason": "Customer cancelled order"
}
```

Optional partial refund:

```json
{
  "orderId": 3001,
  "amount": 100.00,
  "reason": "Partial refund approved"
}
```

**Response 200:**

```json
{
  "success": true,
  "orderId": 3001,
  "orderNumber": "AB1234567890",
  "paymentMethod": "ONLINE",
  "paymentStatus": "REFUNDED",
  "refundStatus": "PROCESSED",
  "refundId": "rfnd_XXXXXXXXXXXX",
  "refundAmount": 485.00,
  "refundReason": "Customer cancelled order",
  "refundedAt": "2026-04-21T12:00:00",
  "message": "Refund processed successfully"
}
```

### 11.4 Get Refund Status

```
GET /api/payment/refund-status/{orderId}
Authorization: Bearer <token>
```

### 11.5 Admin Refund

```
POST /api/admin/orders/{orderId}/refund
Authorization: Bearer <admin token>
Content-Type: application/json
```

```json
{
  "amount": 100.00,
  "reason": "Admin approved refund"
}
```

To fetch admin refund status:

```
GET /api/admin/orders/{orderId}/refund-status
Authorization: Bearer <admin token>
```

---

### 11.6 Webhook (Server-side fallback — NOT called by app)

```
POST /api/payment/webhook
X-Razorpay-Signature: <signature>
```

This is configured in your Razorpay dashboard to handle `payment.captured` / `payment.failed` events. The app should NOT call this.

---

## 12. Wallet

Requires `Authorization: Bearer <token>`.

> **Note:** `userMainId` in wallet endpoints is the `UserMain` table ID, **not** the `customerId`. However, the backend also accepts `customerId` — it resolves to the `userMainId` automatically. Use the `customerId` you received in the login response and the backend will accept it.

### 12.1 Get Wallet Balance

```
GET /api/wallet/balance/{userMainId}
Authorization: Bearer <token>
```

> Pass either `customerId` or `userMainId` from the login response.

**Response 200:**
```json
{ "success": true, "balance": 250.50 }
```

**Error 400:**
```json
{ "error": "Unauthorized wallet access" }
```

---

### 12.2 Get Transaction History

```
GET /api/wallet/history/{userMainId}
Authorization: Bearer <token>
```

**Response 200:**
```json
{
  "success": true,
  "history": [
    {
      "id": 5,
      "type": "CREDIT",
      "amount": 100.00,
      "description": "Welcome bonus",
      "createdAt": "2024-03-01T10:00:00"
    },
    {
      "id": 6,
      "type": "DEBIT",
      "amount": 50.00,
      "description": "Wallet payment",
      "createdAt": "2024-03-10T15:00:00"
    }
  ]
}
```

---

### 12.3 Debit Wallet (Spend Money)

```
POST /api/wallet/debit
Authorization: Bearer <token>
Content-Type: application/json
```

Also accepted at `/api/wallet/spend` and `/api/wallet/deduct`.

**Request body:**
```json
{
  "userMainId": 12,       // or "customerId": 12
  "amount": 50.00,
  "description": "Wallet payment for order GRO-001"
}
```

**Response 200:**
```json
{ "success": true, "message": "Amount debited from wallet successfully" }
```

**Error 400:**
```json
{ "success": false, "error": "Insufficient wallet balance" }
```

---

### 12.4 Add Money to Wallet

```
POST /api/wallet/add
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "userMainId": 12,
  "amount": 100.00,
  "description": "Cashback reward"
}
```

**Response 200:**
```json
{ "success": true, "message": "Money added to wallet successfully" }
```

---

## 13. Notifications

Requires `Authorization: Bearer <token>` for inbox endpoints.

### 13.1 Save FCM Token (Public)

Call after login/refresh to register the device for push notifications.

```
POST /api/save-token
Content-Type: application/json
```

**Request body:**
```json
{
  "phone": "+919876543210",
  "fcmToken": "device_fcm_token_here"
}
```

**Response 200:**
```json
{ "success": true, "message": "Token saved successfully" }
```

---

### 13.2 Get Notifications

```
GET /api/notifications
Authorization: Bearer <token>
```

**Response 200:**
```json
[
  {
    "id": 10,
    "type": "order",
    "channel": "Notifications",
    "title": "Order Placed",
    "body": "Your order GRO-001 has been placed successfully.",
    "data": { "orderId": "88" },
    "isRead": false,
    "createdAt": "2024-03-24T11:00:00"
  }
]
```

---

### 13.3 Mark All Notifications as Read

```
PATCH /api/notifications/read-all
Authorization: Bearer <token>
```

**Response 200:**
```json
{ "success": true, "message": "Notifications marked as read" }
```

---

### 13.4 Mark Single Notification as Read

```
PATCH /api/notifications/{notificationId}/read
Authorization: Bearer <token>
```

**Response 200:**
```json
{
  "success": true,
  "message": "Notification marked as read",
  "notification": { "id": 10, "isRead": true }
}
```

**Error 404:**
```json
{ "success": false, "message": "Notification not found" }
```

---

## 14. Wishlist

Requires `Authorization: Bearer <token>`.

### 14.1 Get Wishlist

```
GET /api/customer/products/wishlist
Authorization: Bearer <token>
```

**Response 200:** Array of product objects (same shape as 5.1).

---

### 14.2 Add to Wishlist

```
POST /api/customer/products/wishlist
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{ "productId": 101 }
```

**Response 200:**
```json
{ "success": true, "message": "Product added to wishlist" }
```

---

### 14.3 Remove from Wishlist

```
DELETE /api/customer/products/wishlist?productId=101
Authorization: Bearer <token>
```

**Response 200:**
```json
{ "success": true, "message": "Product removed from wishlist" }
```

---

### 14.4 Merge Guest Wishlist (on login)

```
POST /api/customer/products/wishlist/merge
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{ "productIds": [101, 102, 105] }
```

**Response 200:**
```json
{ "success": true, "message": "Wishlist merged successfully" }
```

---

## 15. Ratings

### 15.1 Submit Rating

Requires `Authorization: Bearer <token>`.

```
POST /api/customer/products/rating
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "productId": 101,
  "rating": 4,            // 1–5
  "comment": "Good product, fast delivery"  // optional
}
```

**Response 200:**
```json
{ "success": true, "message": "Rating submitted successfully" }
```

**Error 400:**
```json
{ "error": "You must have purchased this product to rate it" }
```

---

### 15.2 Get Product Ratings (Public)

```
GET /api/customer/products/{productId}/ratings
```

**Response 200:**
```json
{
  "success": true,
  "ratings": [
    {
      "id": 1,
      "rating": 4,
      "comment": "Good product",
      "customerId": 12,
      "customerName": "Ravi Kumar",
      "createdAt": "2024-03-24T11:00:00"
    }
  ]
}
```

---

## 16. Tracking

### 16.1 Get Order Tracking (Public)

```
GET /api/tracking/{orderNumber}
```

> Also listen to Firebase RTDB at `tracking/{orderNumber}` for real-time location updates (recommended over polling this endpoint).

**Response 200 (while in transit):**
```json
{
  "orderNumber": "GRO-20240324-1001",
  "status": "EN_ROUTE_TO_CUSTOMER",
  "deliveryPersonName": "Ravi Kumar",
  "deliveryPersonPhone": "+919876543210",
  "lat": 17.385,
  "lng": 78.4867,
  "updatedAt": "2024-03-24T11:00:00",
  "isLive": true
}
```

**Response 200 (order delivered / not yet dispatched):**
```json
{
  "orderNumber": "GRO-20240324-1001",
  "status": "DELIVERED",
  "isLive": false,
  "message": "Order has been delivered. Enjoy!"
}
```

**Order Status Values:**
| Status | Description |
|--------|-------------|
| `PENDING` | Order pending — awaiting processing |
| `PLACED` | Order placed — waiting for store confirmation |
| `STORE_NOTIFIED` | Store notified |
| `STORE_ACCEPTED` | Store accepted |
| `STORE_REJECTED` | Store rejected |
| `BROADCASTED_TO_RIDERS` | Looking for nearby delivery rider |
| `RIDER_ASSIGNED` | Delivery rider assigned |
| `REACHED_STORE` | Rider reached the store |
| `PICKUP_OTP_VERIFIED` | Rider verified pickup OTP |
| `PICKED_UP` | Rider picked up the order |
| `OUT_FOR_DELIVERY` | Order on the way |
| `EN_ROUTE_TO_CUSTOMER` | Rider heading to customer |
| `DELIVERED` | Delivered |
| `CANCELLED` | Cancelled |

---

## 17. Banners

### 17.1 Get Active Banners (Public)

```
GET /api/customer/banners
```

**Response 200:**
```json
{
  "success": true,
  "banners": [
    {
      "id": 1,
      "name": "Summer Sale",
      "imageUrl": "https://s3.amazonaws.com/.../banner1.jpg",
      "videoUrl": null,
      "targetUrl": null,
      "displayOrder": 1,
      "isActive": true,
      "actionType": "CATEGORY",   // CATEGORY | PRODUCT | URL | null
      "actionValue": "2"          // categoryId / productId / URL
    }
  ]
}
```

---

## 18. Marquee

### 18.1 Get Active Marquee Text (Public)

Returns scrolling announcement text shown in the app header.

```
GET /api/marquee
```

**Response 200:**
```json
[
  {
    "id": 1,
    "text": "Free delivery on orders above ₹200!",
    "active": true,
    "displayOrder": 1
  },
  {
    "id": 2,
    "text": "Use code WELCOME10 for 10% off your first order",
    "active": true,
    "displayOrder": 2
  }
]
```

---

## 19. Policies

### 19.1 Get Policy by Type (Public)

```
GET /api/policies/{type}
```

**Types:** `privacy-policy` | `terms-and-conditions` | `refund-policy` | `shipping-policy` (any string you've saved via admin)

**Response 200:**
```json
{
  "success": true,
  "policy": {
    "id": 1,
    "type": "privacy-policy",
    "content": "# Privacy Policy\n\n...",
    "updatedAt": "2024-03-01T10:00:00"
  }
}
```

**Error 404:** Policy not found.

---

## 20. App Settings & Version

### 20.1 Get App Settings (Public)

Returns delivery charge, platform fee, payment methods enabled, and other app-level settings in one call.

```
GET /api/settings
```

**Response 200:**
```json
{
  "deliveryCharge": 20.00,
  "platformFee": 2.00,
  "onlinePaymentEnabled": true,
  "cashOnDeliveryEnabled": true,
  "handlingCharge": 0.00,
  "smallCartFee": 0.00,
  "smallCartThreshold": 0.00,
  "maxItemsPerOrder": 50,
  "gstPercentage": 0.00
}
```

---

### 20.2 Get Latest App Version (Public)

Use to check if an update is available and prompt the user.

```
GET /api/app/version
```

**Response 200:**
```json
{
  "latestVersion": "1.0.64",
  "minVersion": "1.0.0",
  "forceUpdate": false,
  "releaseNotes": "Bug fixes and performance improvements.",
  "android": {
    "storeUrl": "https://play.google.com/store/apps/details?id=com.anusha.deliveryapp"
  },
  "ios": {
    "storeUrl": "https://apps.apple.com/app/anusha-bazaar/id000000000"
  }
}
```

> **Note:** `latestVersion` is a hardcoded constant in `AppVersionController.java`. Update it to the current app version with every Play Store release.

---

## Error Handling

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request — check error/fields in body |
| 401 | Unauthorized — JWT missing, expired, or invalid |
| 403 | Forbidden — resource belongs to another user |
| 404 | Not found |
| 409 | Conflict — e.g., phone already registered |
| 500 | Internal server error |

**Standard error body:**
```json
{ "error": "Human-readable error message" }
```

Some endpoints also return the richer envelope below:
```json
{
  "timestamp": "2024-03-24T11:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable error message"
}
```

**Validation error body (400) — when required fields are missing or invalid:**
```json
{
  "timestamp": "2024-03-24T11:00:00",
  "status": 400,
  "error": "Validation Failed",
  "fields": {
    "latitude": "latitude is required — get from device GPS",
    "longitude": "longitude is required — get from device GPS"
  }
}
```
> Check the `fields` object — each key is the field name, value is the error message.

**Unknown field error (400) — when request body contains unsupported JSON keys:**
```json
{
  "timestamp": "2024-03-24T11:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Unknown field: totalAmount"
}
```

---

## Authentication Summary

| Endpoint | Auth Required |
|----------|--------------|
| `/api/auth/*` | No |
| `/api/categories`, `/api/subcategories`, `/api/products/*` | No |
| `/api/coupons/active` | No |
| `/api/checkout-settings`, `/api/settings` | No |
| `/api/tracking/*` | No |
| `/api/customer/banners` | No |
| `/api/marquee` | No |
| `/api/policies/*` | No |
| `/api/app/version` | No |
| `/api/customer/products/*/ratings` (GET) | No |
| `/api/cart/*` | Yes |
| `/api/addresses/*` | Yes |
| `/api/orders/*` | Yes |
| `/api/payment/*` | Yes |
| `/api/wallet/*` | Yes |
| `/api/notifications/*` | Yes |
| `/api/customer/profile` | Yes |
| `/api/customer/products/wishlist` | Yes |
| `/api/customer/products/rating` (POST) | Yes |

---

## Known Limitations / Bugs

1. **App version hardcoded:** `GET /api/app/version` returns a hardcoded version string in `AppVersionController.java`. Update `LATEST_VERSION` constant every time you release a new build to the Play Store.

2. **`PUT /api/addresses/{id}` is still full-update semantics:** For partial actions like “set default,” the client must still resend the full address payload including lat/lng.

3. **`handlingCharge` and `smallCartFee` are currently exposed as `0.00` in order response:** The fields are present for frontend compatibility, but no dynamic backend calculation is implemented yet.
