package chat

import (
	"sync"
	"time"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

const sessionTTL = 30 * time.Minute

// Message is a single turn in a conversation.
type Message struct {
	Role    string // "user" | "model"
	Content string
}

// Session holds the state for an ongoing job chat.
type Session struct {
	Job      scorer.ScoredJob
	History  []Message
	LastSeen time.Time
}

// Store manages in-memory chat sessions keyed by Telegram chatID.
type Store struct {
	mu       sync.Mutex
	sessions map[int64]*Session
}

func NewStore() *Store {
	s := &Store{sessions: make(map[int64]*Session)}
	go s.runCleanup()
	return s
}

// Start creates or replaces a session for the given chatID.
func (s *Store) Start(chatID int64, job scorer.ScoredJob) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sessions[chatID] = &Session{
		Job:      job,
		History:  nil,
		LastSeen: time.Now(),
	}
}

// Get returns the active session for a chatID, or nil if none/expired.
func (s *Store) Get(chatID int64) *Session {
	s.mu.Lock()
	defer s.mu.Unlock()
	sess, ok := s.sessions[chatID]
	if !ok {
		return nil
	}
	if time.Since(sess.LastSeen) > sessionTTL {
		delete(s.sessions, chatID)
		return nil
	}
	return sess
}

// Append adds a message to the session history and updates LastSeen.
func (s *Store) Append(chatID int64, role, content string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	sess, ok := s.sessions[chatID]
	if !ok {
		return
	}
	sess.History = append(sess.History, Message{Role: role, Content: content})
	sess.LastSeen = time.Now()
}

// End removes the session for a chatID.
func (s *Store) End(chatID int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.sessions, chatID)
}

// runCleanup periodically removes expired sessions.
func (s *Store) runCleanup() {
	ticker := time.NewTicker(5 * time.Minute)
	for range ticker.C {
		s.mu.Lock()
		for id, sess := range s.sessions {
			if time.Since(sess.LastSeen) > sessionTTL {
				delete(s.sessions, id)
			}
		}
		s.mu.Unlock()
	}
}
