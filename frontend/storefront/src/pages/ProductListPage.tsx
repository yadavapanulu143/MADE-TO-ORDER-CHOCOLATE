import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogApi } from '../api/endpoints';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { money } from '../lib/format';
import type { ProductSummary } from '../api/types';

const PAGE_SIZE = 12;

export function ProductListPage() {
  const [params, setParams] = useSearchParams();
  const search = params.get('search') ?? '';
  const page = Number(params.get('page') ?? '0');

  const [items, setItems] = useState<ProductSummary[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<unknown>(null);
  const [term, setTerm] = useState(search);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    catalogApi
      .listProducts({ page, size: PAGE_SIZE, search: search || undefined })
      .then((result) => {
        if (cancelled) return;
        setItems(result.content);
        setTotal(result.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [page, search]);

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams();
    if (term.trim()) next.set('search', term.trim());
    setParams(next);
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="page">
      <header className="page-head">
        <h1>Shop chocolate</h1>
        <form className="search-form" onSubmit={submitSearch} role="search">
          <label className="sr-only" htmlFor="search">
            Search products
          </label>
          <input
            id="search"
            type="search"
            placeholder="Search by name…"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
          <button type="submit" className="btn btn-primary">
            Search
          </button>
        </form>
      </header>

      {error ? <ErrorNotice error={error} /> : null}
      {!items && !error ? <Spinner label="Loading products…" /> : null}

      {items && items.length === 0 ? (
        <EmptyState title="No chocolate matched your search">
          <p className="muted">Try a different word, or browse everything.</p>
          <Link to="/products" className="btn btn-secondary">
            Clear search
          </Link>
        </EmptyState>
      ) : null}

      {items && items.length > 0 ? (
        <>
          <ul className="product-grid">
            {items.map((product) => (
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
                    {!product.available ? <p className="badge badge-neutral">Currently unavailable</p> : null}
                  </div>
                </Link>
              </li>
            ))}
          </ul>

          {totalPages > 1 ? (
            <nav className="pagination" aria-label="Pagination">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={page <= 0}
                onClick={() => {
                  const next = new URLSearchParams(params);
                  next.set('page', String(page - 1));
                  setParams(next);
                }}
              >
                Previous
              </button>
              <span className="muted">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={page + 1 >= totalPages}
                onClick={() => {
                  const next = new URLSearchParams(params);
                  next.set('page', String(page + 1));
                  setParams(next);
                }}
              >
                Next
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
