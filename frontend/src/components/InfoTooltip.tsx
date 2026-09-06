import React, { useState, useRef, useEffect, useId } from 'react';
import { createPortal } from 'react-dom';
import { Info } from 'lucide-react';

export interface InfoTooltipProps {
  content: string;
  title?: string;
  className?: string;
  size?: number;
  ariaLabel?: string;
}

export default function InfoTooltip({
  content,
  title,
  className = '',
  size = 13,
  ariaLabel,
}: InfoTooltipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number; placeAbove: boolean }>({
    top: 0,
    left: 0,
    placeAbove: false,
  });

  const triggerRef = useRef<HTMLButtonElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const closeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const tooltipId = useId();

  const updatePosition = () => {
    if (!triggerRef.current) return;
    const rect = triggerRef.current.getBoundingClientRect();
    const tooltipWidth = Math.min(280, window.innerWidth - 24);
    const estimatedHeight = 90;
    const viewportPadding = 12;

    // Horizontal clamping: center on trigger, clamp between 12px and (innerWidth - width - 12px)
    let left = rect.left + rect.width / 2 - tooltipWidth / 2;
    left = Math.max(viewportPadding, Math.min(left, window.innerWidth - tooltipWidth - viewportPadding));

    // Vertical positioning: render above if there's enough room, else below
    const spaceAbove = rect.top;
    const placeAbove = spaceAbove >= estimatedHeight + viewportPadding;
    const proposedTop = placeAbove ? rect.top - estimatedHeight - 8 : rect.bottom + 8;
    const maxTop = window.innerHeight - estimatedHeight - viewportPadding;
    const top = Math.max(viewportPadding, Math.min(proposedTop, maxTop));

    setCoords({ top, left, placeAbove });
  };

  const handleOpen = () => {
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    updatePosition();
    setIsOpen(true);
  };

  const handleClose = () => {
    closeTimeoutRef.current = setTimeout(() => {
      setIsOpen(false);
    }, 120);
  };

  const handlePointerEnter = (e: React.PointerEvent) => {
    if (e.pointerType === 'mouse') {
      handleOpen();
    }
  };

  const handlePointerLeave = (e: React.PointerEvent) => {
    if (e.pointerType === 'mouse') {
      handleClose();
    }
  };

  const handleToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    if (isOpen) {
      setIsOpen(false);
    } else {
      updatePosition();
      setIsOpen(true);
    }
  };

  // Adjust exact position once rendered using measured height and clamp vertically
  useEffect(() => {
    if (isOpen && tooltipRef.current && triggerRef.current) {
      const triggerRect = triggerRef.current.getBoundingClientRect();
      const tooltipRect = tooltipRef.current.getBoundingClientRect();
      const viewportPadding = 12;

      let left = triggerRect.left + triggerRect.width / 2 - tooltipRect.width / 2;
      left = Math.max(viewportPadding, Math.min(left, window.innerWidth - tooltipRect.width - viewportPadding));

      const spaceAbove = triggerRect.top;
      const placeAbove = spaceAbove >= tooltipRect.height + viewportPadding;
      const proposedTop = placeAbove
        ? triggerRect.top - tooltipRect.height - 8
        : triggerRect.bottom + 8;

      const maxTop = window.innerHeight - tooltipRect.height - viewportPadding;
      const top = Math.max(viewportPadding, Math.min(proposedTop, maxTop));

      setCoords({ top, left, placeAbove });
    }
  }, [isOpen]);

  // Handle escape key, outside click, and window resize/scroll
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    };

    const handleOutsideClick = (e: MouseEvent | TouchEvent) => {
      const target = e.target as Node;
      if (
        triggerRef.current?.contains(target) ||
        tooltipRef.current?.contains(target)
      ) {
        return;
      }
      setIsOpen(false);
    };

    const handleScrollOrResize = () => {
      setIsOpen(false);
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handleOutsideClick);
    document.addEventListener('touchstart', handleOutsideClick);
    window.addEventListener('scroll', handleScrollOrResize, true);
    window.addEventListener('resize', handleScrollOrResize);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('touchstart', handleOutsideClick);
      window.removeEventListener('scroll', handleScrollOrResize, true);
      window.removeEventListener('resize', handleScrollOrResize);
    };
  }, [isOpen]);

  useEffect(() => {
    return () => {
      if (closeTimeoutRef.current) {
        clearTimeout(closeTimeoutRef.current);
      }
    };
  }, []);

  const tooltipPortal = isOpen && typeof document !== 'undefined'
    ? createPortal(
        <div
          ref={tooltipRef}
          id={tooltipId}
          role="tooltip"
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
          onClick={(e) => e.stopPropagation()}
          style={{
            position: 'fixed',
            top: `${coords.top}px`,
            left: `${coords.left}px`,
            width: `min(280px, calc(100vw - 24px))`,
            maxHeight: 'calc(100vh - 24px)',
            overflowY: 'auto',
          }}
          className="z-[9999] rounded-lg border border-slate-700 bg-slate-900 p-3 text-xs text-slate-100 shadow-2xl transition-opacity duration-150 pointer-events-auto"
        >
          {title && (
            <p className="font-semibold text-slate-100 mb-1 leading-snug">
              {title}
            </p>
          )}
          <p className="text-slate-300 leading-relaxed break-words font-normal">
            {content}
          </p>
        </div>,
        document.body
      )
    : null;

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-describedby={isOpen ? tooltipId : undefined}
        aria-label={ariaLabel || (title ? `Information about ${title}` : 'More information')}
        aria-expanded={isOpen}
        onClick={handleToggle}
        onPointerEnter={handlePointerEnter}
        onPointerLeave={handlePointerLeave}
        onFocus={handleOpen}
        onBlur={handleClose}
        className={`inline-flex items-center justify-center min-w-[24px] min-h-[24px] sm:min-w-[28px] sm:min-h-[28px] p-1 rounded-md text-slate-400 hover:text-slate-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-colors cursor-help ${className}`}
      >
        <Info size={size} className="shrink-0" />
      </button>
      {tooltipPortal}
    </>
  );
}
