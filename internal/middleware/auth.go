package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/nqhhdev/ivelox-core/internal/infrastructure/supabase"
)

func Auth(jwtSecret string) gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			c.AbortWithStatusJSON(401, gin.H{"error": "missing or invalid authorization header"})
			return
		}

		tokenStr := strings.TrimPrefix(header, "Bearer ")
		claims, err := supabase.VerifyJWT(tokenStr, jwtSecret)
		if err != nil {
			c.AbortWithStatusJSON(401, gin.H{"error": "invalid token"})
			return
		}

		c.Set("userID", claims.Sub)
		c.Set("userEmail", claims.Email)
		c.Next()
	}
}
