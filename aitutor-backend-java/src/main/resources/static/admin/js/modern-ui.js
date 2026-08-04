// Modern UI Enhancements
class ModernUI {
    constructor() {
        this.init();
    }

    init() {
        this.initSidebarToggle();
        this.initNotifications();
        this.initTableSorting();
        this.initSearchEnhancements();
        this.initChartControls();
        this.initTooltips();
    }

    // Sidebar Toggle for Mobile
    initSidebarToggle() {
        const toggleBtn = document.querySelector('.sidebar-toggle');
        const sidebar = document.querySelector('.sidebar');
        
        if (toggleBtn && sidebar) {
            toggleBtn.addEventListener('click', () => {
                sidebar.classList.toggle('mobile-open');
                document.body.classList.toggle('sidebar-open');
            });

            // Close sidebar when clicking outside on mobile
            document.addEventListener('click', (e) => {
                if (window.innerWidth <= 1024 && 
                    !sidebar.contains(e.target) && 
                    !toggleBtn.contains(e.target)) {
                    sidebar.classList.remove('mobile-open');
                    document.body.classList.remove('sidebar-open');
                }
            });
        }
    }

    // Enhanced Notification System
    initNotifications() {
        this.notificationContainer = document.getElementById('notificationContainer');
        if (!this.notificationContainer) {
            this.notificationContainer = document.createElement('div');
            this.notificationContainer.id = 'notificationContainer';
            this.notificationContainer.className = 'notification-container';
            document.body.appendChild(this.notificationContainer);
        }
    }

    showNotification(message, type = 'info', duration = 5000) {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        
        const icons = {
            success: 'fas fa-check-circle',
            error: 'fas fa-exclamation-circle',
            warning: 'fas fa-exclamation-triangle',
            info: 'fas fa-info-circle'
        };

        notification.innerHTML = `
            <div class="notification-icon">
                <i class="${icons[type]}"></i>
            </div>
            <div class="notification-content">
                <div class="notification-message">${message}</div>
            </div>
            <button class="notification-close">
                <i class="fas fa-times"></i>
            </button>
        `;

        // Add close functionality
        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.addEventListener('click', () => {
            this.removeNotification(notification);
        });

        // Auto remove after duration
        setTimeout(() => {
            this.removeNotification(notification);
        }, duration);

        this.notificationContainer.appendChild(notification);
        
        // Trigger animation
        setTimeout(() => {
            notification.style.opacity = '1';
            notification.style.transform = 'translateX(0)';
        }, 10);
    }

    removeNotification(notification) {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(100%)';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 300);
    }

    // Table Sorting Enhancement
    initTableSorting() {
        const tables = document.querySelectorAll('.data-table');
        
        tables.forEach(table => {
            const headers = table.querySelectorAll('th');
            
            headers.forEach((header, index) => {
                if (header.textContent.trim() && !header.querySelector('input')) {
                    header.classList.add('sortable');
                    header.addEventListener('click', () => {
                        this.sortTable(table, index, header);
                    });
                }
            });
        });
    }

    sortTable(table, columnIndex, header) {
        const tbody = table.querySelector('tbody');
        const rows = Array.from(tbody.querySelectorAll('tr'));
        
        // Determine sort direction
        const isAsc = header.classList.contains('asc');
        
        // Clear all sort classes
        table.querySelectorAll('th').forEach(th => {
            th.classList.remove('asc', 'desc');
        });
        
        // Set new sort class
        header.classList.add(isAsc ? 'desc' : 'asc');
        
        // Sort rows
        rows.sort((a, b) => {
            const aText = a.cells[columnIndex].textContent.trim();
            const bText = b.cells[columnIndex].textContent.trim();
            
            // Try to parse as numbers
            const aNum = parseFloat(aText);
            const bNum = parseFloat(bText);
            
            if (!isNaN(aNum) && !isNaN(bNum)) {
                return isAsc ? bNum - aNum : aNum - bNum;
            }
            
            // String comparison
            return isAsc ? bText.localeCompare(aText) : aText.localeCompare(bText);
        });
        
        // Re-append sorted rows
        rows.forEach(row => tbody.appendChild(row));
    }

    // Enhanced Search with Debouncing
    initSearchEnhancements() {
        const searchInputs = document.querySelectorAll('input[type="text"][placeholder*="搜索"]');
        
        searchInputs.forEach(input => {
            let debounceTimer;
            
            input.addEventListener('input', (e) => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => {
                    this.performSearch(e.target);
                }, 300);
            });
            
            // Add search icon animation
            const parent = input.parentElement;
            if (parent.classList.contains('search-group')) {
                const icon = parent.querySelector('i');
                if (icon) {
                    input.addEventListener('focus', () => {
                        icon.style.color = 'var(--primary-color)';
                    });
                    
                    input.addEventListener('blur', () => {
                        icon.style.color = 'var(--text-muted)';
                    });
                }
            }
        });
    }

    performSearch(input) {
        // This would integrate with your existing search functionality
        const searchTerm = input.value.toLowerCase();
        const table = input.closest('.tab-content')?.querySelector('.data-table tbody');
        
        if (table) {
            const rows = table.querySelectorAll('tr');
            
            rows.forEach(row => {
                const text = row.textContent.toLowerCase();
                const shouldShow = text.includes(searchTerm);
                row.style.display = shouldShow ? '' : 'none';
            });
        }
    }

    // Chart Controls Enhancement
    initChartControls() {
        const chartControls = document.querySelectorAll('.chart-controls');
        
        chartControls.forEach(controls => {
            const buttons = controls.querySelectorAll('.btn-chart');
            
            buttons.forEach(button => {
                button.addEventListener('click', () => {
                    // Remove active class from siblings
                    buttons.forEach(btn => btn.classList.remove('active'));
                    
                    // Add active class to clicked button
                    button.classList.add('active');
                    
                    // Trigger chart update (integrate with your chart logic)
                    const chartType = button.dataset.chart || button.dataset.period;
                    const chartContainer = controls.closest('.chart-card');
                    const canvas = chartContainer.querySelector('canvas');
                    
                    if (canvas && window.chartInstances) {
                        // This would integrate with your existing chart update logic
                        console.log(`Updating chart: ${canvas.id} to ${chartType}`);
                    }
                });
            });
        });
    }

    // Tooltip System
    initTooltips() {
        const tooltipElements = document.querySelectorAll('[data-tooltip]');
        
        tooltipElements.forEach(element => {
            element.addEventListener('mouseenter', (e) => {
                this.showTooltip(e.target, e.target.dataset.tooltip);
            });
            
            element.addEventListener('mouseleave', () => {
                this.hideTooltip();
            });
        });
    }

    showTooltip(element, text) {
        const tooltip = document.createElement('div');
        tooltip.className = 'tooltip-popup';
        tooltip.textContent = text;
        
        document.body.appendChild(tooltip);
        
        const rect = element.getBoundingClientRect();
        tooltip.style.left = rect.left + (rect.width / 2) - (tooltip.offsetWidth / 2) + 'px';
        tooltip.style.top = rect.top - tooltip.offsetHeight - 8 + 'px';
        
        setTimeout(() => {
            tooltip.classList.add('visible');
        }, 10);
    }

    hideTooltip() {
        const tooltip = document.querySelector('.tooltip-popup');
        if (tooltip) {
            tooltip.remove();
        }
    }

    // Loading States
    showLoading(element) {
        const loader = document.createElement('div');
        loader.className = 'loading-overlay';
        loader.innerHTML = `
            <div class="loading-spinner">
                <div class="loading"></div>
                <span>加载中...</span>
            </div>
        `;
        
        element.style.position = 'relative';
        element.appendChild(loader);
    }

    hideLoading(element) {
        const loader = element.querySelector('.loading-overlay');
        if (loader) {
            loader.remove();
        }
    }

    // Smooth Scrolling
    smoothScrollTo(element) {
        element.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    }

    // Form Validation Enhancement
    validateForm(form) {
        const inputs = form.querySelectorAll('input[required], select[required], textarea[required]');
        let isValid = true;
        
        inputs.forEach(input => {
            if (!input.value.trim()) {
                this.showFieldError(input, '此字段为必填项');
                isValid = false;
            } else {
                this.clearFieldError(input);
            }
        });
        
        return isValid;
    }

    showFieldError(input, message) {
        this.clearFieldError(input);
        
        const error = document.createElement('div');
        error.className = 'field-error';
        error.textContent = message;
        
        input.parentNode.appendChild(error);
        input.classList.add('error');
    }

    clearFieldError(input) {
        const error = input.parentNode.querySelector('.field-error');
        if (error) {
            error.remove();
        }
        input.classList.remove('error');
    }

    // Animation Utilities
    fadeIn(element, duration = 300) {
        element.style.opacity = '0';
        element.style.display = 'block';
        
        let start = null;
        const animate = (timestamp) => {
            if (!start) start = timestamp;
            const progress = timestamp - start;
            
            element.style.opacity = Math.min(progress / duration, 1);
            
            if (progress < duration) {
                requestAnimationFrame(animate);
            }
        };
        
        requestAnimationFrame(animate);
    }

    fadeOut(element, duration = 300) {
        let start = null;
        const animate = (timestamp) => {
            if (!start) start = timestamp;
            const progress = timestamp - start;
            
            element.style.opacity = Math.max(1 - (progress / duration), 0);
            
            if (progress < duration) {
                requestAnimationFrame(animate);
            } else {
                element.style.display = 'none';
            }
        };
        
        requestAnimationFrame(animate);
    }
}

// Initialize Modern UI when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.modernUI = new ModernUI();
});

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ModernUI;
}