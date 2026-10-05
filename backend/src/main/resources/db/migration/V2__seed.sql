INSERT INTO questions (question_number, clue, answer, direction) VALUES
(1,  'Object-oriented language whose mascot is Duke and runs on a virtual machine', 'JAVA', 'ACROSS'),
(2,  'Distributed version control system created by Linus Torvalds', 'GIT', 'DOWN'),
(3,  'Markup language that gives web pages their structure', 'HTML', 'ACROSS'),
(4,  'Standard language for querying relational databases', 'SQL', 'DOWN'),
(5,  'Component-based UI library from Meta, used with JSX', 'REACT', 'ACROSS'),
(6,  'Smallest unit of digital information, either 0 or 1', 'BIT', 'DOWN'),
(7,  'Secure version of HTTP that uses TLS encryption', 'HTTPS', 'ACROSS'),
(8,  'Open-source kernel with a penguin mascot named Tux', 'LINUX', 'DOWN'),
(9,  'Tool that packages apps into containers, logo is a whale', 'DOCKER', 'ACROSS'),
(10, 'Simple sorting algorithm that repeatedly swaps adjacent out-of-order items', 'BUBBLE', 'DOWN'),
(11, 'Last-in, first-out data structure', 'STACK', 'ACROSS'),
(12, 'High-level language named after a British comedy troupe', 'PYTHON', 'DOWN'),
(13, 'The chip that executes a computer program instructions', 'PROCESSOR', 'ACROSS'),
(14, 'Device that forwards data packets between networks', 'ROUTER', 'DOWN'),
(15, 'Lightweight data format built from curly braces and key-value pairs', 'JSON', 'ACROSS'),
(16, 'Technique where a function calls itself', 'RECURSION', 'DOWN'),
(17, 'Google family of generative AI models', 'GEMINI', 'ACROSS'),
(18, 'Language used to style web pages', 'CSS', 'DOWN'),
(19, 'Programming construct that repeats a block, such as for or while', 'LOOP', 'ACROSS'),
(20, 'Fast volatile memory that loses its contents when power is off', 'RAM', 'DOWN');

INSERT INTO round2_challenges (title, description, requirements, time_limit_minutes, active) VALUES (
 'Build a Campus Event Hub',
 'Using any AI tools you like, build and deploy a responsive website that helps students discover and register interest in campus events.',
 '1. Home page listing at least 6 events with title, date, venue and category.
2. Working category filter and text search.
3. Event detail view with a Register interest button that shows a confirmation message.
4. Fully responsive layout (mobile, tablet, desktop).
5. Clean, consistent visual design.
6. Public GitHub repository with a README.',
 90,
 TRUE);
