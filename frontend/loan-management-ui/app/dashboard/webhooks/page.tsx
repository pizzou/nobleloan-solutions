
'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { webhookApi } from '@/services/api';
import { WebhookEndpoint } from '@/types';

import {
  Card,
  CardHeader,
  CardBody,
} from '@/components/ui/Card';

import { Button } from '@/components/ui/Button';

import {
  Table,
  Thead,
  Th,
  Tbody,
  Tr,
  Td,
} from '@/components/ui/Table';

import { Modal } from '@/components/ui/Modal';

import {
  FormGroup,
  Input,
  Alert,
} from '@/components/ui/Form';

const ALL_EVENTS = [
  'LOAN_CREATED',
  'LOAN_APPROVED',
  'LOAN_REJECTED',
  'LOAN_DISBURSED',
  'PAYMENT_MADE',
  'LOAN_OVERDUE',
  'LOAN_DEFAULTED',
] as const;

type WebhookEvent = (typeof ALL_EVENTS)[number];

interface WebhookForm {
  url: string;
  description: string;
  subscribedEvents: string[];
}

const DEFAULT_FORM: WebhookForm = {
  url: '',
  description: '',
  subscribedEvents: [...ALL_EVENTS],
};

export default function WebhooksPage() {
  // ================================================================
  // STATE
  // ================================================================

  const [webhooks, setWebhooks] =
    useState<WebhookEndpoint[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [refreshing, setRefreshing] =
    useState(false);

  const [saving, setSaving] =
    useState(false);

  const [deletingId, setDeletingId] =
    useState<number | null>(null);

  const [addOpen, setAddOpen] =
    useState(false);

  const [msg, setMsg] =
    useState('');

  const [successMsg, setSuccessMsg] =
    useState('');

  const [lastRefresh, setLastRefresh] =
    useState<Date | null>(null);

  const [form, setForm] =
    useState<WebhookForm>({
      ...DEFAULT_FORM,
      subscribedEvents: [...DEFAULT_FORM.subscribedEvents],
    });

  const mountedRef =
    useRef(true);

  const loadingRef =
    useRef(false);

  // ================================================================
  // CLEANUP
  // ================================================================

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // ================================================================
  // LOAD WEBHOOKS
  // ================================================================

  const load = useCallback(
    async (showSpinner = true) => {
      if (loadingRef.current) {
        return;
      }

      loadingRef.current = true;

      try {
        if (showSpinner) {
          setLoading(true);
        } else {
          setRefreshing(true);
        }

        const response =
          await webhookApi.list();

        /*
         * Support both:
         *
         * 1. Direct array response
         * 2. { data: [...] } response
         *
         * This makes the page more tolerant of API wrappers.
         */

        const data =
          Array.isArray(response)
            ? response
            : Array.isArray(
                (response as any)?.data
              )
              ? (response as any).data
              : [];

        if (!mountedRef.current) {
          return;
        }

        setWebhooks(data);

        setLastRefresh(
          new Date()
        );
      } catch (error: any) {
        console.error(
          'Failed to load webhooks:',
          error
        );

        if (mountedRef.current) {
          setMsg(
            error?.message ||
              'Failed to load webhook endpoints.'
          );
        }
      } finally {
        loadingRef.current = false;

        if (mountedRef.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    []
  );

  // ================================================================
  // INITIAL LOAD
  // ================================================================

  useEffect(() => {
    load(true);
  }, [load]);

  // ================================================================
  // AUTO REFRESH
  // ================================================================

  useEffect(() => {
    const interval =
      window.setInterval(() => {
        load(false);
      }, 10000);

    return () => {
      window.clearInterval(interval);
    };
  }, [load]);

  // ================================================================
  // OPEN ADD MODAL
  // ================================================================

  const openAddModal = () => {
    setMsg('');
    setSuccessMsg('');

    setForm({
      ...DEFAULT_FORM,
      subscribedEvents: [
        ...DEFAULT_FORM.subscribedEvents,
      ],
    });

    setAddOpen(true);
  };

  // ================================================================
  // CLOSE ADD MODAL
  // ================================================================

  const closeAddModal = () => {
    if (saving) {
      return;
    }

    setAddOpen(false);

    setMsg('');
  };

  // ================================================================
  // CREATE WEBHOOK
  // ================================================================

  const handleAdd = async (
    e?: React.FormEvent
  ) => {
    e?.preventDefault();

    setMsg('');
    setSuccessMsg('');

    const url =
      form.url.trim();

    const description =
      form.description.trim();

    const subscribedEvents =
      form.subscribedEvents
        .map(event => event.trim())
        .filter(Boolean);

    // --------------------------------------------------------------
    // VALIDATE URL
    // --------------------------------------------------------------

    if (!url) {
      setMsg(
        'Webhook endpoint URL is required.'
      );
      return;
    }

    try {
      const parsed =
        new URL(url);

      if (
        parsed.protocol !== 'https:'
        &&
        parsed.hostname !== 'localhost'
        &&
        parsed.hostname !== '127.0.0.1'
      ) {
        setMsg(
          'Production webhook endpoints must use HTTPS.'
        );
        return;
      }
    } catch {
      setMsg(
        'Please enter a valid webhook URL.'
      );
      return;
    }

    // --------------------------------------------------------------
    // VALIDATE EVENTS
    // --------------------------------------------------------------

    if (
      subscribedEvents.length === 0
    ) {
      setMsg(
        'Select at least one webhook event.'
      );
      return;
    }

    // --------------------------------------------------------------
    // PAYMENT EVENT SAFETY
    // --------------------------------------------------------------

    if (
      !subscribedEvents.includes(
        'PAYMENT_MADE'
      )
    ) {
      const confirmed =
        window.confirm(
          'PAYMENT_MADE is not selected. This endpoint will not receive borrower payment events. Continue?'
        );

      if (!confirmed) {
        return;
      }
    }

    setSaving(true);

    try {
      /*
       * Do NOT generate the webhook secret here.
       *
       * The backend must generate it securely.
       */

      const payload = {
        url,
        description,
        subscribedEvents,
        active: true,
      };

      await webhookApi.create(
        payload
      );

      if (!mountedRef.current) {
        return;
      }

      setAddOpen(false);

      setForm({
        ...DEFAULT_FORM,
        subscribedEvents: [
          ...DEFAULT_FORM.subscribedEvents,
        ],
      });

      setSuccessMsg(
        subscribedEvents.includes(
          'PAYMENT_MADE'
        )
          ? 'Webhook endpoint created successfully. PAYMENT_MADE is enabled.'
          : 'Webhook endpoint created successfully.'
      );

      await load(false);
    } catch (error: any) {
      console.error(
        'Failed to create webhook:',
        error
      );

      if (mountedRef.current) {
        setMsg(
          error?.response?.data?.message ||
            error?.message ||
            'Failed to create webhook endpoint.'
        );
      }
    } finally {
      if (mountedRef.current) {
        setSaving(false);
      }
    }
  };

  // ================================================================
  // DELETE WEBHOOK
  // ================================================================

  const handleDelete = async (
    id: number
  ) => {
    if (!id) {
      return;
    }

    const confirmed =
      window.confirm(
        'Delete this webhook endpoint?\n\nAll future events will stop being delivered to this endpoint.'
      );

    if (!confirmed) {
      return;
    }

    setDeletingId(id);
    setMsg('');
    setSuccessMsg('');

    try {
      await webhookApi.remove(id);

      if (!mountedRef.current) {
        return;
      }

      setSuccessMsg(
        'Webhook endpoint deleted successfully.'
      );

      await load(false);
    } catch (error: any) {
      console.error(
        'Failed to delete webhook:',
        error
      );

      if (mountedRef.current) {
        setMsg(
          error?.response?.data?.message ||
            error?.message ||
            'Failed to delete webhook endpoint.'
        );
      }
    } finally {
      if (mountedRef.current) {
        setDeletingId(null);
      }
    }
  };

  // ================================================================
  // TOGGLE EVENT
  // ================================================================

  const toggleEvent = (
    event: WebhookEvent
  ) => {
    setForm(current => {
      const exists =
        current.subscribedEvents.includes(
          event
        );

      return {
        ...current,
        subscribedEvents: exists
          ? current.subscribedEvents.filter(
              existing =>
                existing !== event
            )
          : [
              ...current.subscribedEvents,
              event,
            ],
      };
    });
  };

  // ================================================================
  // SELECT ALL EVENTS
  // ================================================================

  const selectAllEvents = () => {
    setForm(current => ({
      ...current,
      subscribedEvents: [
        ...ALL_EVENTS,
      ],
    }));
  };

  // ================================================================
  // CLEAR ALL EVENTS
  // ================================================================

  const clearAllEvents = () => {
    setForm(current => ({
      ...current,
      subscribedEvents: [],
    }));
  };

  // ================================================================
  // FORMAT DATE
  // ================================================================

  const formatDate = (
    value: unknown
  ) => {
    if (!value) {
      return '—';
    }

    try {
      const date =
        new Date(
          value as string
        );

      if (
        Number.isNaN(
          date.getTime()
        )
      ) {
        return '—';
      }

      return date.toLocaleString();
    } catch {
      return '—';
    }
  };

  // ================================================================
  // STATUS
  // ================================================================

  const renderDeliveryStatus = (
    webhook: WebhookEndpoint
  ) => {
    if (
      !webhook.lastDeliveryStatus
    ) {
      return (
        <span className="text-xs text-gray-400">
          No delivery yet
        </span>
      );
    }

    const status =
      webhook.lastDeliveryStatus;

    const success =
      status
        .toUpperCase()
        .startsWith('SUCCESS');

    return (
      <div className="flex flex-col gap-1">
        <span
          className={
            `inline-flex w-fit items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full ${
              success
                ? 'bg-green-100 text-green-700'
                : 'bg-red-100 text-red-600'
            }`
          }
        >
          {success
            ? '● SUCCESS'
            : '● FAILED'}
        </span>

        {!success && (
          <span
            className="text-[10px] text-red-400 max-w-[220px] break-words"
          >
            {status}
          </span>
        )}
      </div>
    );
  };

  // ================================================================
  // EVENT COUNT
  // ================================================================

  const eventCount =
    form.subscribedEvents.length;

  // ================================================================
  // ACTIVE COUNT
  // ================================================================

  const activeCount =
    webhooks.filter(
      webhook =>
        webhook.active
    ).length;

  // ================================================================
  // FAILED COUNT
  // ================================================================

  const failedCount =
    webhooks.filter(
      webhook =>
        (webhook.failureCount ?? 0) > 0
    ).length;

  // ================================================================
  // PAGE
  // ================================================================

  return (
    <div>
      {/* ============================================================
          HEADER
          ============================================================ */}

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">
            Webhook Endpoints
          </h1>

          <p className="text-sm text-gray-500 mt-0.5">
            Real-time event delivery to external systems using
            signed HTTPS POST requests.
          </p>

          {lastRefresh && (
            <p className="text-[11px] text-gray-400 mt-1">
              Last refreshed:{' '}
              {lastRefresh.toLocaleTimeString()}
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            loading={refreshing}
            onClick={() =>
              load(false)
            }
          >
            ↻ Refresh
          </Button>

          <Button
            icon="+"
            onClick={openAddModal}
          >
            Add Endpoint
          </Button>
        </div>
      </div>

      {/* ============================================================
          ALERTS
          ============================================================ */}

      {msg && (
        <div className="mb-4">
          <Alert type="error">
            {msg}
          </Alert>
        </div>
      )}

      {successMsg && (
        <div className="mb-4">
          <Alert type="success">
            {successMsg}
          </Alert>
        </div>
      )}

      {/* ============================================================
          SUMMARY
          ============================================================ */}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <div className="text-xs text-gray-500">
            Total Endpoints
          </div>

          <div className="text-2xl font-bold text-gray-900 mt-1">
            {webhooks.length}
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <div className="text-xs text-gray-500">
            Active Endpoints
          </div>

          <div className="text-2xl font-bold text-green-600 mt-1">
            {activeCount}
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <div className="text-xs text-gray-500">
            Endpoints With Failures
          </div>

          <div className="text-2xl font-bold text-red-600 mt-1">
            {failedCount}
          </div>
        </div>
      </div>

      {/* ============================================================
          INFORMATION
          ============================================================ */}

      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-5 text-sm text-blue-700">
        <strong>
          How it works:
        </strong>{' '}
        When events occur, such as a borrower payment, loan approval,
        or loan disbursement, the backend sends an HTTPS POST request
        to your registered endpoint.

        <ul className="list-disc ml-5 mt-2 space-y-1">
          <li>
            Event name
          </li>

          <li>
            Organization ID
          </li>

          <li>
            Event timestamp
          </li>

          <li>
            Event data
          </li>

          <li>
            HMAC-SHA256 signature
          </li>
        </ul>
      </div>

      {/* ============================================================
          PAYMENT WEBHOOK WARNING
          ============================================================ */}

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-5 text-sm text-amber-800">
        <div className="font-semibold mb-1">
          Payment webhook monitoring
        </div>

        <div>
          Borrower payments generate:
          <code className="ml-1 bg-amber-100 px-1.5 py-0.5 rounded font-mono text-xs">
            PAYMENT_MADE
          </code>
        </div>

        <div className="mt-1 text-xs text-amber-700">
          The endpoint must be active and subscribed to
          <strong className="ml-1">
            PAYMENT_MADE
          </strong>
          for the event to be delivered.
        </div>
      </div>

      {/* ============================================================
          WEBHOOK TABLE
          ============================================================ */}

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="w-7 h-7 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : webhooks.length === 0 ? (
          <div className="py-14 text-center">
            <div className="text-5xl mb-3">
              🔗
            </div>

            <div className="font-semibold text-gray-700">
              No webhooks configured
            </div>

            <div className="text-sm text-gray-400 mt-1">
              Add an endpoint to receive real-time events.
            </div>

            <div className="mt-4">
              <Button
                icon="+"
                onClick={openAddModal}
              >
                Add Webhook Endpoint
              </Button>
            </div>
          </div>
        ) : (
          <Table>
            <Thead>
              <tr>
                <Th>
                  URL
                </Th>

                <Th>
                  Description
                </Th>

                <Th>
                  Events
                </Th>

                <Th>
                  Endpoint
                </Th>

                <Th>
                  Last Delivery
                </Th>

                <Th>
                  Status
                </Th>

                <Th>
                  Failures
                </Th>

                <Th>
                  Actions
                </Th>
              </tr>
            </Thead>

            <Tbody>
              {webhooks.map(
                webhook => (
                  <Tr
                    key={
                      webhook.id
                    }
                  >
                    {/* URL */}

                    <Td>
                      <code className="text-xs bg-gray-100 px-2 py-1 rounded font-mono break-all">
                        {webhook.url}
                      </code>
                    </Td>

                    {/* DESCRIPTION */}

                    <Td className="text-sm text-gray-600">
                      {webhook.description ||
                        '—'}
                    </Td>

                    {/* EVENTS */}

                    <Td>
                      <div className="flex flex-wrap gap-1">
                        {(
                          webhook.subscribedEvents ??
                          []
                        )
                          .slice(
                            0,
                            3
                          )
                          .map(
                            event => (
                              <span
                                key={event}
                                className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded font-mono"
                              >
                                {event.replace(
                                  /_/g,
                                  ' '
                                )}
                              </span>
                            )
                          )}

                        {(
                          webhook.subscribedEvents ??
                          []
                        ).length > 3 && (
                          <span className="text-[10px] text-gray-400">
                            +
                            {(
                              webhook.subscribedEvents ??
                              []
                            ).length -
                              3}
                          </span>
                        )}
                      </div>

                      {(
                        webhook.subscribedEvents ??
                        []
                      ).includes(
                        'PAYMENT_MADE'
                      ) && (
                        <div className="text-[9px] text-green-600 mt-1 font-semibold">
                          ✓ PAYMENT_MADE enabled
                        </div>
                      )}
                    </Td>

                    {/* ACTIVE */}

                    <Td>
                      <span
                        className={
                          `inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full ${
                            webhook.active
                              ? 'bg-green-100 text-green-700'
                              : 'bg-red-100 text-red-600'
                          }`
                        }
                      >
                        {webhook.active
                          ? '● Active'
                          : '● Disabled'}
                      </span>
                    </Td>

                    {/* LAST DELIVERY */}

                    <Td className="text-xs text-gray-500">
                      {formatDate(
                        webhook.lastDeliveryAt
                      )}
                    </Td>

                    {/* STATUS */}

                    <Td>
                      {renderDeliveryStatus(
                        webhook
                      )}
                    </Td>

                    {/* FAILURES */}

                    <Td
                      className={
                        `font-semibold ${
                          (
                            webhook.failureCount ??
                            0
                          ) > 0
                            ? 'text-red-500'
                            : 'text-gray-400'
                        }`
                      }
                    >
                      {webhook.failureCount ??
                        0}
                    </Td>

                    {/* ACTIONS */}

                    <Td>
                      <Button
                        variant="ghost"
                        size="xs"
                        loading={
                          deletingId ===
                          webhook.id
                        }
                        onClick={() =>
                          handleDelete(
                            webhook.id!
                          )
                        }
                      >
                        🗑
                      </Button>
                    </Td>
                  </Tr>
                )
              )}
            </Tbody>
          </Table>
        )}
      </Card>

      {/* ============================================================
          ADD WEBHOOK MODAL
          ============================================================ */}

      <Modal
        open={addOpen}
        onClose={closeAddModal}
        title="Add Webhook Endpoint"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={
                closeAddModal
              }
              disabled={saving}
            >
              Cancel
            </Button>

            <Button
              loading={saving}
              onClick={() =>
                handleAdd()
              }
            >
              Save Endpoint
            </Button>
          </>
        }
      >
        <form
          onSubmit={handleAdd}
        >
          {/* ERROR */}

          {msg && (
            <div className="mb-4">
              <Alert type="error">
                {msg}
              </Alert>
            </div>
          )}

          {/* URL */}

          <FormGroup
            label="Endpoint URL"
            required
          >
            <Input
              type="url"
              required
              placeholder="https://yourapp.com/webhooks/loansaas"
              value={form.url}
              onChange={e =>
                setForm(
                  current => ({
                    ...current,
                    url:
                      e.target.value,
                  })
                )
              }
            />

            <p className="text-[11px] text-gray-400 mt-1">
              Production endpoints must use HTTPS.
            </p>
          </FormGroup>

          {/* DESCRIPTION */}

          <FormGroup
            label="Description"
          >
            <Input
              placeholder="e.g. Core banking integration"
              value={
                form.description
              }
              onChange={e =>
                setForm(
                  current => ({
                    ...current,
                    description:
                      e.target.value,
                  })
                )
              }
            />
          </FormGroup>

          {/* EVENTS */}

          <FormGroup
            label="Subscribed Events"
          >
            <div className="flex items-center gap-2 mb-2">
              <Button
                type="button"
                variant="secondary"
                size="xs"
                onClick={
                  selectAllEvents
                }
              >
                Select all
              </Button>

              <Button
                type="button"
                variant="secondary"
                size="xs"
                onClick={
                  clearAllEvents
                }
              >
                Clear all
              </Button>

              <span className="text-[11px] text-gray-400">
                {eventCount} selected
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-1">
              {ALL_EVENTS.map(
                event => (
                  <label
                    key={event}
                    className="flex items-center gap-2 cursor-pointer text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={form.subscribedEvents.includes(
                        event
                      )}
                      onChange={() =>
                        toggleEvent(
                          event
                        )
                      }
                      className="w-4 h-4 text-teal-600 rounded border-gray-300 focus:ring-teal-500"
                    />

                    <span className="text-gray-700 font-mono text-xs">
                      {event.replace(
                        /_/g,
                        ' '
                      )}
                    </span>
                  </label>
                )
              )}
            </div>
          </FormGroup>

          {/* PAYMENT WARNING */}

          {!form.subscribedEvents.includes(
            'PAYMENT_MADE'
          ) && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700 mt-2">
              <strong>
                ⚠️ PAYMENT_MADE is not selected.
              </strong>

              <div className="mt-1">
                This endpoint will not receive borrower
                payment events.
              </div>
            </div>
          )}

          {/* SECURITY */}

          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-xs text-yellow-700 mt-4">
            <strong>
              🔐 Webhook security
            </strong>

            <div className="mt-1">
              The backend will generate a unique signing
              secret automatically. Do not generate the
              secret in the browser.
            </div>

            <div className="mt-1">
              Receiving systems should verify the
              <code className="mx-1 bg-yellow-100 px-1 rounded">
                X-Webhook-Signature
              </code>
              HMAC-SHA256 header.
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
