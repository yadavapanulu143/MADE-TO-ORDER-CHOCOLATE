import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { catalogApi } from '../api/endpoints';
import { useSettings } from '../lib/useSettings';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { money } from '../lib/format';
import type { ProductSummary } from '../api/types';

export function HomePage() {
  const settings = useSettings();
  const [products, setProducts] = useState<ProductSummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    catalogApi
      .listProducts({ size: 6 })
      .then((page) => {
        if (!cancelled) setProducts(page.content);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="home">
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">Made to order</p>
          <h1>Chocolate, made only after you pay.</h1>
          <p className="lede">
            Every bar is prepared after your order is confirmed, so it reaches you fresh. Browse the
            range, choose your variant, and we start work the moment your payment is verified.
          </p>
          <div className="hero-actions">
            <Link to="/products" className="btn btn-primary btn-lg">
              Shop the range
            </Link>
            <Link to="/serviceability" className="btn btn-secondary btn-lg">
              Check delivery area
            </Link>
          </div>
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="hero-card">
            <span className="hero-emoji">🍫</span>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Our chocolate</h2>
          <Link to="/products" className="link">
            See everything
          </Link>
        </div>
        {error ? <ErrorNotice error={error} /> : null}
        {!products && !error ? <Spinner label="Loading products…" /> : null}
        {products ? (
          <ul className="product-grid">
            {products.map((product) => (
              <li key={product.id} className="product-card">
                <Link to={`/products/${product.slug}`} className="product-link">
                  <div className="product-image">
                    {product.primaryImageUrl ? (
                      <img src={product.primaryImageUrl} alt={product.name} loading="lazy" />
                    ) : (
                      <span className="product-image-placeholder" aria-hidden="true">
                        🍫
                      </span>
                    )}
                  </div>
                  <div className="product-body">
                    <h3>{product.name}</h3>
                    {product.shortDescription ? <p className="muted">{product.shortDescription}</p> : null}
                    <p className="price">
                      From {money(product.fromPriceMinorUnits, product.fromPrice, product.currency)}
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      {settings.refundPolicy ? (
        <section className="section trust">
          <h2>Refunds</h2>
          <p>{settings.refundPolicy}</p>
        </section>
      ) : null}
    </div>
  );
}
