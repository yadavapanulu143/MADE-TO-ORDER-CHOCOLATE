import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { catalogApi } from '../api/endpoints';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { money } from '../lib/format';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { ApiError } from '../api/client';
import type { Product, Variant } from '../api/types';

export function ProductDetailPage() {
  const { slug = '' } = useParams();
  const navigate = useNavigate();
  const { signedIn, customer } = useAuth();
  const { addLine } = useCart();

  const [product, setProduct] = useState<Product | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [selected, setSelected] = useState<Variant | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState<unknown>(null);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setProduct(null);
    setError(null);
    catalogApi
      .getProduct(slug)
      .then((value) => {
        if (cancelled) return;
        setProduct(value);
        const firstActive = value.variants.find((variant) => variant.active) ?? value.variants[0] ?? null;
        setSelected(firstActive);
        setQuantity(firstActive ? Math.max(1, firstActive.minOrderQuantity) : 1);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [slug]);

  const primaryImage = useMemo(() => {
    if (!product) return null;
    return product.images.find((image) => image.primary) ?? product.images[0] ?? null;
  }, [product]);

  const fullyVerified = customer?.fullyVerified ?? false;

  async function handleAdd() {
    if (!selected) return;
    setAddError(null);
    setAdded(false);
    setAdding(true);
    try {
      await addLine(selected.id, quantity);
      setAdded(true);
    } catch (err) {
      setAddError(err);
    } finally {
      setAdding(false);
    }
  }

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <div className="page">
        <ErrorNotice error={error} />
        {notFound ? (
          <Link to="/products" className="btn btn-secondary">
            Back to the shop
          </Link>
        ) : null}
      </div>
    );
  }

  if (!product) return <Spinner label="Loading product…" />;

  const min = selected?.minOrderQuantity ?? 1;
  const max = selected?.maxOrderQuantity ?? 10;

  return (
    <div className="page product-detail">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/products">Shop</Link>
        <span aria-hidden="true">/</span>
        <span>{product.name}</span>
      </nav>

      <div className="detail-grid">
        <div className="detail-media">
          <div className="detail-image">
            {primaryImage ? (
              <img src={primaryImage.url} alt={primaryImage.altText ?? product.name} />
            ) : (
              <span className="product-image-placeholder" aria-hidden="true">
                🍫
              </span>
            )}
          </div>
          {product.images.length > 1 ? (
            <ul className="thumb-row">
              {product.images.map((image) => (
                <li key={image.id}>
                  <img src={image.url} alt={image.altText ?? product.name} />
                </li>
              ))}
            </ul>
          ) : null}
        </div>

        <div className="detail-info">
          <h1>{product.name}</h1>
          {product.shortDescription ? <p className="lede">{product.shortDescription}</p> : null}

          <p className="price price-lg">
            {selected
              ? money(selected.priceMinorUnits, selected.price, selected.currency)
              : money(product.fromPriceMinorUnits, product.fromPrice, product.currency)}
          </p>

          {product.variants.length > 0 ? (
            <fieldset className="variant-picker">
              <legend>Choose a variant</legend>
              <div className="variant-options">
                {product.variants.map((variant) => (
                  <label
                    key={variant.id}
                    className={`variant-option${selected?.id === variant.id ? ' selected' : ''}${
                      variant.active ? '' : ' disabled'
                    }`}
                  >
                    <input
                      type="radio"
                      name="variant"
                      value={variant.id}
                      checked={selected?.id === variant.id}
                      disabled={!variant.active}
                      onChange={() => {
                        setSelected(variant);
                        setQuantity(Math.max(1, variant.minOrderQuantity));
                        setAdded(false);
                      }}
                    />
                    <span className="variant-label">{variant.label}</span>
                    <span className="variant-price">{money(variant.priceMinorUnits, variant.price, variant.currency)}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          ) : null}

          {selected ? (
            <div className="quantity-row">
              <label htmlFor="quantity">Quantity</label>
              <div className="stepper">
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label="Decrease quantity"
                  onClick={() => setQuantity((current) => Math.max(min, current - 1))}
                  disabled={quantity <= min}
                >
                  −
                </button>
                <input
                  id="quantity"
                  type="number"
                  min={min}
                  max={max}
                  value={quantity}
                  onChange={(event) => {
                    const parsed = Number(event.target.value);
                    if (Number.isNaN(parsed)) return;
                    setQuantity(Math.min(max, Math.max(min, parsed)));
                  }}
                />
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label="Increase quantity"
                  onClick={() => setQuantity((current) => Math.min(max, current + 1))}
                  disabled={quantity >= max}
                >
                  +
                </button>
              </div>
              <p className="muted small">
                Minimum {min}, maximum {max} per order
              </p>
            </div>
          ) : null}

          {addError ? <ErrorNotice error={addError} /> : null}
          {added ? (
            <div className="notice notice-success" role="status">
              <p>Added to your cart.</p>
              <Link to="/cart" className="btn btn-primary">
                Go to cart
              </Link>
            </div>
          ) : null}

          {!signedIn ? (
            <p className="muted">
              <Link to="/login">Sign in</Link> to add this to your cart.
            </p>
          ) : !fullyVerified ? (
            <p className="notice notice-warn">
              Please verify your email and mobile number before ordering.{' '}
              <button type="button" className="link" onClick={() => navigate('/verify')}>
                Verify now
              </button>
            </p>
          ) : (
            <button
              type="button"
              className="btn btn-primary btn-lg"
              onClick={handleAdd}
              disabled={adding || !selected || !selected.active}
            >
              {adding ? 'Adding…' : 'Add to cart'}
            </button>
          )}

          {product.attributes.length > 0 ? (
            <dl className="attributes">
              {product.attributes.map((attribute) => (
                <div key={attribute.id}>
                  <dt>{attribute.name}</dt>
                  <dd>{attribute.value}</dd>
                </div>
              ))}
            </dl>
          ) : null}
        </div>
      </div>

      {product.description ? (
        <section className="section prose">
          <h2>About this chocolate</h2>
          <p>{product.description}</p>
        </section>
      ) : null}
      {product.preparationInfo ? (
        <section className="section prose">
          <h2>Preparation</h2>
          <p>{product.preparationInfo}</p>
        </section>
      ) : null}
      {product.deliveryInfo ? (
        <section className="section prose">
          <h2>Delivery</h2>
          <p>{product.deliveryInfo}</p>
        </section>
      ) : null}
    </div>
  );
}
