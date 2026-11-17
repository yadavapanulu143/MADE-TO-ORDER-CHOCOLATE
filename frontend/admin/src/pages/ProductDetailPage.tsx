import { useCallback, useRef, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { catalogAdminApi, inventoryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money, toRupeeInput } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';
import type { Variant } from '../api/types';

export function ProductDetailPage() {
  const { productId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => catalogAdminApi.get(productId), [productId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [description, setDescription] = useState('');
  const [preparationInfo, setPreparationInfo] = useState('');
  const [deliveryInfo, setDeliveryInfo] = useState('');
  const [editingVariant, setEditingVariant] = useState<Variant | null>(null);
  const [label, setLabel] = useState('');
  const [sku, setSku] = useState('');
  const [price, setPrice] = useState('');
  const [minQty, setMinQty] = useState('1');
  const [maxQty, setMaxQty] = useState('10');
  const [attrName, setAttrName] = useState('');
  const [attrValue, setAttrValue] = useState('');
  const [altText, setAltText] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const writable = can(Permissions.PRODUCT_WRITE);

  function hydrate() {
    if (!data) return;
    setName(data.name);
    setShortDescription(data.shortDescription ?? '');
    setDescription(data.description ?? '');
    setPreparationInfo(data.preparationInfo ?? '');
    setDeliveryInfo(data.deliveryInfo ?? '');
  }

  async function run(work: () => Promise<unknown>) {
    setBusy(true);
    setActionError(null);
    try {
      await work();
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  function resetVariant() {
    setEditingVariant(null);
    setLabel('');
    setSku('');
    setPrice('');
    setMinQty('1');
    setMaxQty('10');
  }

  function startVariantEdit(variant: Variant) {
    setEditingVariant(variant);
    setLabel(variant.label);
    setSku(variant.sku);
    setPrice(toRupeeInput(variant.priceMinorUnits));
    setMinQty(String(variant.minOrderQuantity));
    setMaxQty(String(variant.maxOrderQuantity));
  }

  if (loading) return <Spinner label="Loading the product…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  // Seed the edit form from the loaded product the first time it arrives.
  if (name === '' && data.name !== '') hydrate();

  async function saveProduct(event: FormEvent) {
    event.preventDefault();
    await run(() =>
      catalogAdminApi.update(productId, {
        name,
        shortDescription: shortDescription || undefined,
        description: description || undefined,
        preparationInfo: preparationInfo || undefined,
        deliveryInfo: deliveryInfo || undefined,
      }),
    );
  }

  async function saveVariant(event: FormEvent) {
    event.preventDefault();
    const body = {
      label,
      sku: sku || undefined,
      price: Number(price),
      minOrderQuantity: Number(minQty),
      maxOrderQuantity: Number(maxQty),
    };
    await run(async () => {
      if (editingVariant) await catalogAdminApi.updateVariant(productId, editingVariant.id, body);
      else await catalogAdminApi.addVariant(productId, body);
      resetVariant();
    });
  }

  async function uploadImage(event: FormEvent) {
    event.preventDefault();
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    await run(async () => {
      await catalogAdminApi.uploadImage(productId, file, altText || undefined);
      setAltText('');
      if (fileRef.current) fileRef.current.value = '';
    });
  }

  return (
    <div className="stack">
      <div className="row">
        <h2>{data.name}</h2>
        <StatusBadge status={data.status} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      <div className="detail-grid">
        <Panel title="Summary">
          <Field label="Slug" value={data.slug} mono />
          <Field label="From" value={money(data.fromPriceMinorUnits, data.fromPrice, data.currency)} />
          <Field label="Updated" value={dateTime(data.updatedAt)} />
        </Panel>
        {writable ? (
          <Panel title="Publish state">
            <div className="row">
              <button type="button" className="btn btn-secondary" disabled={busy}
                onClick={() => run(() => catalogAdminApi.publish(productId))}>
                Publish
              </button>
              <button type="button" className="btn btn-secondary" disabled={busy}
                onClick={() => run(() => catalogAdminApi.unpublish(productId))}>
                Unpublish
              </button>
              <button type="button" className="btn btn-danger" disabled={busy}
                onClick={() => run(() => catalogAdminApi.archive(productId))}>
                Archive
              </button>
            </div>
          </Panel>
        ) : null}
      </div>

      {writable ? (
        <Panel title="Details">
          <form className="form" onSubmit={saveProduct}>
            <div className="field">
              <label htmlFor="p-name">Name</label>
              <input id="p-name" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="p-short">Short description</label>
              <input id="p-short" value={shortDescription} onChange={(e) => setShortDescription(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="p-desc">Description</label>
              <textarea id="p-desc" rows={4} value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="p-prep">Preparation information</label>
              <textarea id="p-prep" rows={2} value={preparationInfo} onChange={(e) => setPreparationInfo(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="p-del">Delivery information</label>
              <textarea id="p-del" rows={2} value={deliveryInfo} onChange={(e) => setDeliveryInfo(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Save details
            </button>
          </form>
        </Panel>
      ) : null}

      <Panel title="Variants">
        <table className="table">
          <thead>
            <tr><th>Label</th><th>SKU</th><th>Price</th><th>Min</th><th>Max</th><th>Active</th><th /></tr>
          </thead>
          <tbody>
            {data.variants.map((variant) => (
              <tr key={variant.id}>
                <td>{variant.label}</td>
                <td className="mono">{variant.sku}</td>
                <td>{money(variant.priceMinorUnits, variant.price, variant.currency)}</td>
                <td>{variant.minOrderQuantity}</td>
                <td>{variant.maxOrderQuantity}</td>
                <td>{variant.active ? 'Yes' : 'No'}</td>
                <td>
                  {writable ? (
                    <div className="row">
                      <button type="button" className="btn btn-ghost" onClick={() => startVariantEdit(variant)}>
                        Edit
                      </button>
                      <button type="button" className="btn btn-ghost"
                        onClick={() => run(() => catalogAdminApi.removeVariant(productId, variant.id))}>
                        Remove
                      </button>
                      <RecipeEditor variantId={variant.id} />
                    </div>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {writable ? (
          <form className="form" onSubmit={saveVariant}>
            <h4>{editingVariant ? `Edit ${editingVariant.label}` : 'Add a variant'}</h4>
            <div className="field">
              <label htmlFor="v-label">Label</label>
              <input id="v-label" required value={label} onChange={(e) => setLabel(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="v-sku">SKU</label>
              <input id="v-sku" value={sku} onChange={(e) => setSku(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="v-price">Price (₹)</label>
              <input id="v-price" inputMode="decimal" required value={price} onChange={(e) => setPrice(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="v-min">Minimum order quantity</label>
              <input id="v-min" inputMode="numeric" required value={minQty} onChange={(e) => setMinQty(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="v-max">Maximum order quantity</label>
              <input id="v-max" inputMode="numeric" required value={maxQty} onChange={(e) => setMaxQty(e.target.value)} />
            </div>
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {editingVariant ? 'Save variant' : 'Add variant'}
              </button>
              {editingVariant ? (
                <button type="button" className="btn btn-ghost" onClick={resetVariant}>
                  Cancel
                </button>
              ) : null}
            </div>
          </form>
        ) : null}
      </Panel>

      <Panel title="Images">
        <ul className="image-list">
          {data.images.map((image) => (
            <li key={image.id}>
              <img src={image.url} alt={image.altText ?? ''} width={80} height={80} />
              <div>
                <p className="muted small">{image.altText ?? 'No alt text'}</p>
                {image.primary ? <span className="badge badge-success">Primary</span> : null}
                {writable ? (
                  <div className="row">
                    {!image.primary ? (
                      <button type="button" className="btn btn-ghost"
                        onClick={() => run(() => catalogAdminApi.setPrimaryImage(productId, image.id))}>
                        Make primary
                      </button>
                    ) : null}
                    <button type="button" className="btn btn-ghost"
                      onClick={() => run(() => catalogAdminApi.removeImage(productId, image.id))}>
                      Remove
                    </button>
                  </div>
                ) : null}
              </div>
            </li>
          ))}
        </ul>

        {writable ? (
          <form className="form" onSubmit={uploadImage}>
            <div className="field">
              <label htmlFor="image-file">Upload an image</label>
              <input id="image-file" type="file" accept="image/png,image/jpeg,image/webp" ref={fileRef} required />
            </div>
            <div className="field">
              <label htmlFor="image-alt">Alt text</label>
              <input id="image-alt" value={altText} onChange={(e) => setAltText(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-secondary" disabled={busy}>
              Upload
            </button>
          </form>
        ) : null}
      </Panel>

      <Panel title="Attributes">
        <ul>
          {data.attributes.map((attribute) => (
            <li key={attribute.id}>
              <strong>{attribute.name}</strong>: {attribute.value}{' '}
              {writable ? (
                <button type="button" className="btn btn-ghost"
                  onClick={() => run(() => catalogAdminApi.removeAttribute(productId, attribute.id))}>
                  Remove
                </button>
              ) : null}
            </li>
          ))}
        </ul>

        {writable ? (
          <form
            className="form"
            onSubmit={(event) => {
              event.preventDefault();
              void run(async () => {
                await catalogAdminApi.addAttribute(productId, { name: attrName, value: attrValue });
                setAttrName('');
                setAttrValue('');
              });
            }}
          >
            <div className="field">
              <label htmlFor="a-name">Name</label>
              <input id="a-name" required value={attrName} onChange={(e) => setAttrName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="a-value">Value</label>
              <input id="a-value" required value={attrValue} onChange={(e) => setAttrValue(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-secondary" disabled={busy}>
              Add attribute
            </button>
          </form>
        ) : null}
      </Panel>
    </div>
  );
}

/** Loads and edits the per-unit ingredient consumption for one variant. */
function RecipeEditor({ variantId }: { variantId: string }) {
  const [open, setOpen] = useState(false);
  const load = useCallback(() => inventoryApi.recipe(variantId), [variantId]);
  const { data, error, loading, reload } = useAsync(load, [load]);
  const [lines, setLines] = useState<Array<{ ingredientId: string; perUnit: string }>>([]);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return (
      <button type="button" className="btn btn-ghost" onClick={() => setOpen(true)}>
        Recipe
      </button>
    );
  }

  return (
    <div className="stack">
      <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>
        Close recipe
      </button>
      {loading ? <Spinner label="Loading recipe…" /> : null}
      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {data ? (
        <>
          <p className="muted small">Consumption per single unit of this variant.</p>
          <ul>
            {data.lines.map((line) => (
              <li key={line.ingredientId}>
                {line.ingredientName}: {line.perUnit} {line.unit}
              </li>
            ))}
          </ul>
          <div className="row">
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => setLines(data.lines.map((line) => ({ ingredientId: line.ingredientId, perUnit: String(line.perUnit) })))}
            >
              Edit lines
            </button>
          </div>
        </>
      ) : null}

      {lines.map((line, index) => (
        <div className="row" key={`${line.ingredientId}-${index}`}>
          <input
            aria-label="Ingredient id"
            className="mono"
            value={line.ingredientId}
            onChange={(event) =>
              setLines((current) =>
                current.map((item, i) => (i === index ? { ...item, ingredientId: event.target.value } : item)),
              )
            }
          />
          <input
            aria-label="Per unit"
            inputMode="decimal"
            value={line.perUnit}
            onChange={(event) =>
              setLines((current) =>
                current.map((item, i) => (i === index ? { ...item, perUnit: event.target.value } : item)),
              )
            }
          />
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
          >
            Remove
          </button>
        </div>
      ))}

      <div className="row">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => setLines((current) => [...current, { ingredientId: '', perUnit: '' }])}
        >
          Add line
        </button>
        <button
          type="button"
          className="btn btn-primary"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setSaveError(null);
            try {
              await inventoryApi.configureRecipe(variantId, lines.map((line) => ({
                ingredientId: line.ingredientId,
                perUnit: Number(line.perUnit),
              })));
              setLines([]);
              reload();
            } catch (caught) {
              setSaveError(caught);
            } finally {
              setBusy(false);
            }
          }}
        >
          Save recipe
        </button>
      </div>
      <FieldErrors error={saveError} />
      {saveError ? <ErrorNotice error={saveError} /> : null}
    </div>
  );
}
