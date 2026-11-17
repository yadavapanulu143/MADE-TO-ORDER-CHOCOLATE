import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { cartApi } from '../api/endpoints';
import { readTokens } from '../auth/tokenStore';
import { useAuth } from '../auth/AuthContext';
import type { Cart } from '../api/types';

interface CartState {
  cart: Cart | null;
  itemCount: number;
  loading: boolean;
  addLine: (variantId: string, quantity: number) => Promise<void>;
  updateLine: (variantId: string, quantity: number) => Promise<void>;
  removeLine: (variantId: string) => Promise<void>;
  clear: () => Promise<void>;
  refresh: () => Promise<void>;
}

const CartContext = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { signedIn } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!readTokens()) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      setCart(await cartApi.view());
    } catch {
      setCart(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh, signedIn]);

  const addLine = useCallback(async (variantId: string, quantity: number) => {
    setCart(await cartApi.addLine(variantId, quantity));
  }, []);

  const updateLine = useCallback(async (variantId: string, quantity: number) => {
    setCart(await cartApi.updateLine(variantId, quantity));
  }, []);

  const removeLine = useCallback(async (variantId: string) => {
    setCart(await cartApi.removeLine(variantId));
  }, []);

  const clear = useCallback(async () => {
    await cartApi.clear();
    setCart(null);
  }, []);

  const value = useMemo<CartState>(
    () => ({
      cart,
      itemCount: cart?.totalItems ?? 0,
      loading,
      addLine,
      updateLine,
      removeLine,
      clear,
      refresh,
    }),
    [cart, loading, addLine, updateLine, removeLine, clear, refresh],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used within a CartProvider');
  return context;
}
