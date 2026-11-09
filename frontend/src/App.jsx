import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';
import Docket from './components/Docket.jsx';
import CaseFile from './components/CaseFile.jsx';
import DemoStore from './components/DemoStore.jsx';

export default function App() {
  const [view, setView] = useState('cases');
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loadingList, setLoadingList] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [notice, setNotice] = useState(null);

  const loadList = useCallback(async (keepSelection = true) => {
    try {
      const data = await api.listDisputes();
      setRows(data);
      setSelected((cur) => {
        if (keepSelection && cur && data.some((r) => r.dispute.paypalDisputeId === cur)) return cur;
        return data[0]?.dispute.paypalDisputeId ?? null;
      });
    } catch (e) {
      setNotice({ tone: 'error', text: `Could not load disputes: ${e.message}` });
    } finally {
      setLoadingList(false);
    }
  }, []);

  useEffect(() => {
    loadList(false);
  }, [loadList]);

  async function syncFromPayPal() {
    setSyncing(true);
    setNotice(null);
    try {
      const synced = await api.syncAll();
      await loadList();
      setNotice({
        tone: 'ok',
        text: synced.length === 0 ? 'PayPal has no disputes for this account yet.' : `Updated ${synced.length} dispute${synced.length === 1 ? '' : 's'} from PayPal.`,
      });
    } catch (e) {
      setNotice({ tone: 'error', text: `Sync failed: ${e.message}` });
    } finally {
      setSyncing(false);
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <svg className="brand-mark" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2.5l8 3v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10v-6l8-3z" />
            <path d="M8.5 12l2.5 2.5 4.5-5" />
          </svg>
          <span className="brand-name">DisputeGuard</span>
          <span className="brand-env">Sandbox</span>
        </div>
        <nav className="tabs" aria-label="Sections">
          <button className={view === 'cases' ? 'tab active' : 'tab'} onClick={() => setView('cases')}>
            Disputes
          </button>
          <button className={view === 'store' ? 'tab active' : 'tab'} onClick={() => setView('store')}>
            Demo store
          </button>
        </nav>
        {view === 'cases' && (
          <button className="btn quiet" onClick={syncFromPayPal} disabled={syncing}>
            {syncing ? 'Checking PayPal…' : 'Check PayPal for disputes'}
          </button>
        )}
      </header>

      {notice && (
        <div className={`notice ${notice.tone}`} role="status">
          <span>{notice.text}</span>
          <button className="notice-close" onClick={() => setNotice(null)} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}

      {view === 'cases' ? (
        <main className="desk">
          <Docket rows={rows} loading={loadingList} selected={selected} onSelect={setSelected} />
          {selected ? (
            <CaseFile key={selected} disputeId={selected} onChanged={() => loadList()} />
          ) : (
            !loadingList && (
              <section className="empty-case">
                <h2>No disputes yet</h2>
                <p>
                  When a buyer opens a dispute, PayPal tells DisputeGuard and the case appears here with its
                  evidence already gathered. To try it now, place an order in the demo store, open a dispute as the
                  sandbox buyer, then choose “Check PayPal for disputes”.
                </p>
                <button className="btn" onClick={() => setView('store')}>
                  Go to the demo store
                </button>
              </section>
            )
          )}
        </main>
      ) : (
        <DemoStore />
      )}
    </div>
  );
}
