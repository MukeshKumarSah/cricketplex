import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../context/AuthContext';
import API from '../../api/axios';
import {
  HiOutlineChatBubbleLeftRight,
  HiOutlineXMark,
  HiOutlinePaperAirplane,
  HiOutlineArrowLeft,
  HiOutlineMagnifyingGlass,
  HiOutlineUserGroup,
  HiOutlineUserPlus,
  HiOutlineUserMinus,
  HiOutlineCamera,
} from 'react-icons/hi2';
import './ChatWidget.css';

export default function ChatWidget() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [conversations, setConversations] = useState([]);
  const [activeConv, setActiveConv] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [totalUnread, setTotalUnread] = useState(0);
  const [searchMode, setSearchMode] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [groupMode, setGroupMode] = useState(false);
  const [groupName, setGroupName] = useState('');
  const [groupMembers, setGroupMembers] = useState([]);
  const [showMembers, setShowMembers] = useState(false);
  const [membersList, setMembersList] = useState([]);
  const [addMemberSearch, setAddMemberSearch] = useState('');
  const [addMemberResults, setAddMemberResults] = useState([]);
  const messagesEndRef = useRef(null);
  const clientRef = useRef(null);
  const groupPicRef = useRef(null);

  const isAdmin = user?.role === 'ADMIN';

  /** Render avatar — shows pic if available, otherwise letter initial */
  const Avatar = ({ picUrl, name, isGroupIcon, size = 36 }) => (
    <div className="cw-conv-avatar" style={{ width: size, height: size, minWidth: size }}>
      {picUrl ? (
        <img src={picUrl} alt="" className="cw-avatar-img" />
      ) : isGroupIcon ? (
        <HiOutlineUserGroup size={size * 0.55} />
      ) : (
        <span>{(name || '?')[0].toUpperCase()}</span>
      )}
    </div>
  );

  // Fetch unread count periodically
  const fetchUnread = useCallback(async () => {
    try {
      const { data } = await API.get('/chat/unread');
      setTotalUnread(data.count);
    } catch {}
  }, []);

  // Fetch conversations
  const fetchConversations = useCallback(async () => {
    try {
      const { data } = await API.get('/chat/conversations');
      setConversations(data);
    } catch {}
  }, []);

  // WebSocket connection
  useEffect(() => {
    if (!user) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/chat/${user.id}`, (message) => {
          const msg = JSON.parse(message.body);
          // If viewing this conversation, append message
          setActiveConv((current) => {
            if (current && current.id === msg.conversationId) {
              setMessages((prev) => {
                if (prev.some((m) => m.id === msg.id)) return prev;
                return [...prev, msg];
              });
            }
            return current;
          });
          // Refresh unread & conversations
          fetchUnread();
          fetchConversations();
        });
      },
    });
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [user, fetchUnread, fetchConversations]);

  // Fetch unread on mount and every 30s
  useEffect(() => {
    fetchUnread();
    const interval = setInterval(fetchUnread, 30000);
    return () => clearInterval(interval);
  }, [fetchUnread]);

  // Fetch conversations when panel opens
  useEffect(() => {
    if (open) fetchConversations();
  }, [open, fetchConversations]);

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Open a conversation
  const openConversation = async (conv) => {
    setActiveConv(conv);
    setSearchMode(false);
    setShowMembers(false);
    try {
      const { data } = await API.get(`/chat/messages/${conv.id}`);
      setMessages(data);
      fetchUnread();
      fetchConversations();
    } catch {}
  };

  // Send message
  const sendMessage = async () => {
    if (!input.trim() || !activeConv) return;
    try {
      await API.post(`/chat/messages/${activeConv.id}`, { content: input.trim() });
      setInput('');
    } catch {}
  };

  // Start DM with user
  const startDM = async (otherUser) => {
    try {
      const { data } = await API.post(`/chat/dm/${otherUser.id}`);
      setSearchMode(false);
      setSearchQuery('');
      setSearchResults([]);
      await fetchConversations();
      openConversation({ id: data.conversationId, name: otherUser.name, isGroup: false });
    } catch {}
  };

  // Search users
  useEffect(() => {
    if (!searchQuery.trim()) { setSearchResults([]); return; }
    const timeout = setTimeout(async () => {
      try {
        const { data } = await API.get(`/chat/users/search?q=${encodeURIComponent(searchQuery)}`);
        setSearchResults(data);
      } catch {}
    }, 300);
    return () => clearTimeout(timeout);
  }, [searchQuery]);

  // Create group
  const createGroup = async () => {
    if (!groupName.trim() || groupMembers.length === 0) return;
    try {
      const { data } = await API.post('/chat/group', {
        name: groupName.trim(),
        memberIds: groupMembers.map((m) => m.id),
      });
      setGroupMode(false);
      setGroupName('');
      setGroupMembers([]);
      await fetchConversations();
      openConversation({ id: data.conversationId, name: data.name, isGroup: true });
    } catch {}
  };

  // Fetch group members
  const fetchGroupMembers = async (convId) => {
    try {
      const { data } = await API.get(`/chat/group/${convId}/members`);
      setMembersList(data);
      setShowMembers(true);
    } catch {}
  };

  // Add member to group
  const addMemberToGroup = async (userId) => {
    if (!activeConv) return;
    try {
      await API.post(`/chat/group/${activeConv.id}/add/${userId}`);
      fetchGroupMembers(activeConv.id);
      setAddMemberSearch('');
      setAddMemberResults([]);
    } catch {}
  };

  // Remove member from group
  const removeMemberFromGroup = async (userId) => {
    if (!activeConv) return;
    try {
      await API.delete(`/chat/group/${activeConv.id}/remove/${userId}`);
      fetchGroupMembers(activeConv.id);
    } catch {}
  };

  // Search for add member
  useEffect(() => {
    if (!addMemberSearch.trim()) { setAddMemberResults([]); return; }
    const timeout = setTimeout(async () => {
      try {
        const { data } = await API.get(`/chat/users/search?q=${encodeURIComponent(addMemberSearch)}`);
        setAddMemberResults(data);
      } catch {}
    }, 300);
    return () => clearTimeout(timeout);
  }, [addMemberSearch]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  // Upload group pic
  const handleGroupPicUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file || !activeConv) return;
    try {
      const fd = new FormData();
      fd.append('file', file);
      const { data } = await API.post(`/chat/group/${activeConv.id}/pic`, fd);
      setActiveConv((prev) => ({ ...prev, groupPicUrl: data.groupPicUrl }));
      fetchConversations();
    } catch {}
  };

  if (!user) return null;

  return (
    <>
      {/* Floating bubble */}
      <button className="cw-bubble" onClick={() => setOpen(!open)}>
        {open ? <HiOutlineXMark size={24} /> : <HiOutlineChatBubbleLeftRight size={24} />}
        {!open && totalUnread > 0 && (
          <span className="cw-badge">{totalUnread > 99 ? '99+' : totalUnread}</span>
        )}
      </button>

      {/* Chat panel */}
      {open && (
        <div className="cw-panel">
          {!activeConv && !searchMode && !groupMode ? (
            /* ── Conversation List ── */
            <div className="cw-list">
              <div className="cw-header">
                <span>Messages</span>
                <div className="cw-header-actions">
                  <button onClick={() => setSearchMode(true)} title="New chat">
                    <HiOutlineMagnifyingGlass size={18} />
                  </button>
                  {isAdmin && (
                    <button onClick={() => setGroupMode(true)} title="Create group">
                      <HiOutlineUserGroup size={18} />
                    </button>
                  )}
                </div>
              </div>
              <div className="cw-conversations">
                {conversations.length === 0 && (
                  <div className="cw-empty">No conversations yet. Start one!</div>
                )}
                {conversations.map((c) => (
                  <div
                    key={c.id}
                    className="cw-conv-item"
                    onClick={() => openConversation(c)}
                  >
                    <Avatar
                      picUrl={c.isGroup ? c.groupPicUrl : c.profilePicUrl}
                      name={c.name}
                      isGroupIcon={c.isGroup && !c.groupPicUrl}
                    />
                    <div className="cw-conv-info">
                      <span className="cw-conv-name">{c.name || 'Unknown'}</span>
                      {c.isGroup && c.memberCount && (
                        <span className="cw-conv-meta">{c.memberCount} members</span>
                      )}
                    </div>
                    {c.unread > 0 && (
                      <span className="cw-conv-unread">{c.unread}</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ) : searchMode ? (
            /* ── Search users for new DM ── */
            <div className="cw-list">
              <div className="cw-header">
                <button onClick={() => { setSearchMode(false); setSearchQuery(''); setSearchResults([]); }}>
                  <HiOutlineArrowLeft size={18} />
                </button>
                <span>New Chat</span>
              </div>
              <div className="cw-search-box">
                <input
                  type="text"
                  placeholder="Search by name..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  autoFocus
                />
              </div>
              <div className="cw-conversations">
                {searchResults.map((u) => (
                  <div key={u.id} className="cw-conv-item" onClick={() => startDM(u)}>
                    <Avatar picUrl={u.profilePicUrl} name={u.name} />
                    <div className="cw-conv-info">
                      <span className="cw-conv-name">{u.name}</span>
                      <span className="cw-conv-meta">@{u.username}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : groupMode ? (
            /* ── Create Group ── */
            <div className="cw-list">
              <div className="cw-header">
                <button onClick={() => { setGroupMode(false); setGroupName(''); setGroupMembers([]); setSearchQuery(''); setSearchResults([]); }}>
                  <HiOutlineArrowLeft size={18} />
                </button>
                <span>Create Group</span>
              </div>
              <div className="cw-group-form">
                <input
                  type="text"
                  placeholder="Group name"
                  value={groupName}
                  onChange={(e) => setGroupName(e.target.value)}
                  className="cw-group-name-input"
                />
                <div className="cw-search-box">
                  <input
                    type="text"
                    placeholder="Add members..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
                {searchResults.map((u) => (
                  <div
                    key={u.id}
                    className="cw-conv-item"
                    onClick={() => {
                      if (!groupMembers.some((m) => m.id === u.id)) {
                        setGroupMembers([...groupMembers, u]);
                      }
                    }}
                  >
                    <Avatar picUrl={u.profilePicUrl} name={u.name} />
                    <div className="cw-conv-info">
                      <span className="cw-conv-name">{u.name}</span>
                    </div>
                    <HiOutlineUserPlus size={16} />
                  </div>
                ))}
                {groupMembers.length > 0 && (
                  <div className="cw-group-selected">
                    <div className="cw-group-selected-label">Selected ({groupMembers.length}):</div>
                    {groupMembers.map((m) => (
                      <span key={m.id} className="cw-group-chip" onClick={() => setGroupMembers(groupMembers.filter((x) => x.id !== m.id))}>
                        {m.name} ×
                      </span>
                    ))}
                  </div>
                )}
                <button
                  className="cw-group-create-btn"
                  onClick={createGroup}
                  disabled={!groupName.trim() || groupMembers.length === 0}
                >
                  Create Group
                </button>
              </div>
            </div>
          ) : showMembers ? (
            /* ── Group Members View ── */
            <div className="cw-list">
              <div className="cw-header">
                <button onClick={() => { setShowMembers(false); setAddMemberSearch(''); setAddMemberResults([]); }}>
                  <HiOutlineArrowLeft size={18} />
                </button>
                <span>Members</span>
              </div>
              {isAdmin && (
                <div className="cw-search-box">
                  <input
                    type="text"
                    placeholder="Add member..."
                    value={addMemberSearch}
                    onChange={(e) => setAddMemberSearch(e.target.value)}
                  />
                </div>
              )}
              {addMemberResults.map((u) => (
                <div key={u.id} className="cw-conv-item" onClick={() => addMemberToGroup(u.id)}>
                  <Avatar picUrl={u.profilePicUrl} name={u.name} />
                  <div className="cw-conv-info"><span className="cw-conv-name">{u.name}</span></div>
                  <HiOutlineUserPlus size={16} style={{ color: '#22d3ee' }} />
                </div>
              ))}
              <div className="cw-conversations">
                {membersList.map((m) => (
                  <div key={m.userId} className="cw-conv-item">
                    <Avatar picUrl={m.profilePicUrl} name={m.name} />
                    <div className="cw-conv-info">
                      <span className="cw-conv-name">{m.name}</span>
                      {m.isAdmin && <span className="cw-conv-meta">Admin</span>}
                    </div>
                    {isAdmin && !m.isAdmin && m.userId !== user.id && (
                      <button className="cw-remove-btn" onClick={() => removeMemberFromGroup(m.userId)}>
                        <HiOutlineUserMinus size={16} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ) : (
            /* ── Active Conversation ── */
            <div className="cw-chat">
              <div className="cw-header">
                <button onClick={() => { setActiveConv(null); setMessages([]); setShowMembers(false); }}>
                  <HiOutlineArrowLeft size={18} />
                </button>
                <span className="cw-header-title">{activeConv.name || 'Chat'}</span>
                {activeConv.isGroup && isAdmin && (
                  <>
                    <button onClick={() => groupPicRef.current?.click()} title="Change group pic">
                      <HiOutlineCamera size={18} />
                    </button>
                    <input
                      ref={groupPicRef}
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={handleGroupPicUpload}
                    />
                  </>
                )}
                {activeConv.isGroup && (
                  <button onClick={() => fetchGroupMembers(activeConv.id)} title="Members">
                    <HiOutlineUserGroup size={18} />
                  </button>
                )}
              </div>
              <div className="cw-messages">
                {messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`cw-msg ${msg.senderId === user.id ? 'cw-msg-mine' : 'cw-msg-other'}`}
                  >
                    {msg.senderId !== user.id && (
                      <div className="cw-msg-row">
                        <Avatar picUrl={msg.senderPic} name={msg.senderName} size={24} />
                        <div className="cw-msg-sender">{msg.senderName}</div>
                      </div>
                    )}
                    <div className="cw-msg-bubble">{msg.content}</div>
                    <div className="cw-msg-time">
                      {new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>
              <div className="cw-input-area">
                <input
                  type="text"
                  placeholder="Type a message..."
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  autoFocus
                />
                <button onClick={sendMessage} disabled={!input.trim()}>
                  <HiOutlinePaperAirplane size={18} />
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}
